if type(Config) ~= "table" then
    Config = { Ban = true, Webhook = "" }
end

local IDENTIFIER_FIELDS = { "license", "license2", "steam", "discord", "ip" }

local function sameId(a, b)
    return type(a) == "string" and a ~= "" and a == b
end

local function isSequence(decoded)
    local count = 0
    for key in pairs(decoded) do
        if type(key) ~= "number" then
            return false
        end
        count = count + 1
    end
    return count == #decoded
end

local function normalizeBans(decoded)
    local bans = {}
    if isSequence(decoded) then
        for _, entry in ipairs(decoded) do
            if type(entry) == "table" then
                bans[#bans + 1] = entry
            end
        end
        return bans
    end

    -- Older builds stored { ["ip:1.2.3.4"] = "reason" }.
    for key, value in pairs(decoded) do
        if type(value) == "string" then
            bans[#bans + 1] = {
                reason = value,
                name = "",
                license = "",
                license2 = "",
                steam = "",
                discord = "",
                ip = tostring(key)
            }
        elseif type(value) == "table" then
            bans[#bans + 1] = value
        end
    end
    return bans
end

local function saveBans(bans)
    local ok, encoded = pcall(json.encode, bans, { indent = true })
    if not ok then
        ok, encoded = pcall(json.encode, bans)
    end
    if not ok or type(encoded) ~= "string" then
        print("[RES_Anticheat] Failed to encode banlist.json")
        return false
    end
    local saved = SaveResourceFile(GetCurrentResourceName(), "banlist.json", encoded, -1)
    if not saved then
        print("[RES_Anticheat] Failed to write banlist.json")
        return false
    end
    return true
end

local function loadBans()
    local raw = LoadResourceFile(GetCurrentResourceName(), "banlist.json")
    if not raw or raw == "" then
        saveBans({})
        return {}
    end
    local ok, decoded = pcall(json.decode, raw)
    if not ok or type(decoded) ~= "table" then
        print("[RES_Anticheat] banlist.json could not be read. New bans will not be saved until it is valid JSON.")
        return {}
    end
    return normalizeBans(decoded)
end

local function banMatches(ban, ids)
    if type(ban) ~= "table" or type(ids) ~= "table" then
        return false
    end
    for _, field in ipairs(IDENTIFIER_FIELDS) do
        if sameId(ban[field], ids[field]) then
            return true
        end
    end
    return false
end

local function findBan(ids)
    for _, ban in ipairs(loadBans()) do
        if banMatches(ban, ids) then
            if type(ban.reason) == "string" and ban.reason ~= "" then
                return ban.reason
            end
            return "Banned"
        end
    end
    return nil
end

function ExtractIdentifiers(src)
    local identifiers = {
        steam = "",
        ip = "",
        discord = "",
        license = "",
        license2 = "",
        xbl = "",
        live = ""
    }

    src = tonumber(src)
    if not src or src <= 0 then
        return identifiers
    end

    for i = 0, GetNumPlayerIdentifiers(src) - 1 do
        local id = GetPlayerIdentifier(src, i)
        if type(id) == "string" then
            if id:sub(1, 9) == "license2:" then
                identifiers.license2 = id
            elseif id:sub(1, 8) == "license:" then
                identifiers.license = id
            elseif id:sub(1, 6) == "steam:" then
                identifiers.steam = id
            elseif id:sub(1, 3) == "ip:" then
                identifiers.ip = id
            elseif id:sub(1, 8) == "discord:" then
                identifiers.discord = id
            elseif id:sub(1, 4) == "xbl:" then
                identifiers.xbl = id
            elseif id:sub(1, 5) == "live:" then
                identifiers.live = id
            end
        end
    end

    return identifiers
end

local function steamProfile(steam)
    if type(steam) ~= "string" or steam == "" then
        return "n/a"
    end
    local hex = steam:match("^steam:(%x+)$")
    if not hex then
        return steam
    end
    local dec = tonumber(hex, 16)
    if not dec then
        return steam
    end
    return "https://steamcommunity.com/profiles/" .. dec
end

local function discordId(discord)
    if type(discord) ~= "string" then
        return ""
    end
    return discord:match("^discord:(.+)$") or ""
end

local function sendToDisc(title, message)
    local webhook = Config.Webhook
    if type(webhook) ~= "string" or webhook:sub(1, 4) ~= "http" then
        return
    end
    local payload = json.encode({
        username = "RES_Anticheat",
        embeds = {
            {
                color = 16711680,
                title = title,
                description = message,
                footer = { text = "RES_Anticheat" }
            }
        }
    })
    PerformHttpRequest(webhook, function() end, "POST", payload, { ["Content-Type"] = "application/json" })
end

function BanPlayer(src, reason)
    src = tonumber(src)
    if not src or src <= 0 then
        return false
    end
    local ids = ExtractIdentifiers(src)
    if findBan(ids) then
        return true
    end
    local hasIdentifier = false
    for _, field in ipairs(IDENTIFIER_FIELDS) do
        if ids[field] ~= "" then
            hasIdentifier = true
            break
        end
    end
    if not hasIdentifier then
        print("[RES_Anticheat] Dropped a player, but no license, steam, discord, or ip was available to store")
        return false
    end
    local bans = loadBans()
    bans[#bans + 1] = {
        reason = reason or "Banned",
        name = GetPlayerName(src) or "",
        license = ids.license,
        license2 = ids.license2,
        steam = ids.steam,
        discord = ids.discord,
        ip = ids.ip
    }
    return saveBans(bans)
end

function UnbanPlayer(identifier)
    if type(identifier) ~= "string" or identifier == "" then
        return false
    end
    local bans = loadBans()
    local kept = {}
    local removed = false
    for _, ban in ipairs(bans) do
        local match = false
        for _, field in ipairs(IDENTIFIER_FIELDS) do
            if sameId(ban[field], identifier) then
                match = true
                break
            end
        end
        if match then
            removed = true
        else
            kept[#kept + 1] = ban
        end
    end
    if removed then
        saveBans(kept)
    end
    return removed
end

local function reportDetection(src, eventName, banned)
    local ids = ExtractIdentifiers(src)
    local playerName = GetPlayerName(src) or "unknown"
    local discord = discordId(ids.discord)
    local mention = discord:match("^%d+$") and ("<@" .. discord .. ">") or "n/a"
    local title
    if banned then
        title = "[BANNED] CONFIRMED HACKER (Tried executing `" .. eventName .. "`): _[" .. tostring(src) .. "] " .. playerName .. "_"
    else
        title = "CONFIRMED HACKER [Tried executing `" .. eventName .. "`]: _[" .. tostring(src) .. "] " .. playerName .. "_"
    end
    sendToDisc(title,
        "Steam: **" .. steamProfile(ids.steam) .. "**\n" ..
        "GameLicense: **" .. (ids.license ~= "" and ids.license or "n/a") .. "**\n" ..
        "License2: **" .. (ids.license2 ~= "" and ids.license2 or "n/a") .. "**\n" ..
        "Discord Tag: **" .. mention .. "**\n" ..
        "Discord UID: **" .. (discord ~= "" and discord or "n/a") .. "**\n" ..
        "IP: **" .. (ids.ip ~= "" and ids.ip or "n/a") .. "**")
end

local function handleOldEvent(src, eventName)
    src = tonumber(src)
    if not src or src <= 0 then
        print("[RES_Anticheat] Ignored server-side trigger of old event '" .. eventName .. "'")
        return
    end
    local reason = "Lua execution: " .. eventName
    local banned = Config.Ban and true or false
    if banned then
        BanPlayer(src, reason)
    end
    reportDetection(src, eventName, banned)
    DropPlayer(src, reason)
end

Citizen.CreateThread(function()
    while true do
        Wait(10000)
        local ok, err = pcall(function()
            local bans = loadBans()
            if #bans == 0 then
                return
            end
            for _, id in pairs(GetPlayers()) do
                local playerOk, playerErr = pcall(function()
                    local ids = ExtractIdentifiers(id)
                    for _, ban in ipairs(bans) do
                        if banMatches(ban, ids) then
                            local reason = ban.reason
                            if type(reason) ~= "string" or reason == "" then
                                reason = "Banned"
                            end
                            DropPlayer(id, "[RES_Anticheat] " .. reason)
                            break
                        end
                    end
                end)
                if not playerOk then
                    print("[RES_Anticheat] Ban poll failed for player " .. tostring(id) .. ": " .. tostring(playerErr))
                end
            end
        end)
        if not ok then
            print("[RES_Anticheat] Ban poll failed: " .. tostring(err))
        end
    end
end)

AddEventHandler("playerConnecting", function(name, setKickReason, deferrals)
    deferrals.defer()
    local src = source
    Wait(0)
    local blockedReason = nil
    local ok, err = pcall(function()
        blockedReason = findBan(ExtractIdentifiers(src))
    end)
    if not ok then
        print("[RES_Anticheat] Ban check failed, allowing connection: " .. tostring(err))
        deferrals.done()
        return
    end
    if blockedReason then
        print("[RES_Anticheat] (BANNED PLAYER) " .. (GetPlayerName(src) or name or "unknown") .. " tried to join, but was banned for: " .. blockedReason)
        deferrals.done("[RES_Anticheat] " .. blockedReason)
        CancelEvent()
        return
    end
    deferrals.done()
end)

local BlacklistedEvents = Events or {}
for i = 1, #BlacklistedEvents do
    local eventName = BlacklistedEvents[i]
    if type(eventName) == "string" and eventName ~= "" then
        RegisterNetEvent(eventName)
        AddEventHandler(eventName, function()
            local src = source
            local ok, err = pcall(function()
                handleOldEvent(src, eventName)
            end)
            if not ok then
                print("[RES_Anticheat] Handler error for '" .. eventName .. "': " .. tostring(err))
            end
        end)
    end
end
