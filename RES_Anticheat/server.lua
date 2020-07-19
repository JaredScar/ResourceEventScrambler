
webhookURL = ''

function BanPlayer(src, reason)
    local config = LoadResourceFile(GetCurrentResourceName(), "banlist.json")
    local cfg = json.decode(config)
    local ids = ExtractIdentifiers(src);
    local ip = ids.ip;
    cfg[tostring(ip)] = reason;
    SaveResourceFile(GetCurrentResourceName(), "banlist.json", json.encode(cfg, { indent = true }), -1)
end
function UnbanPlayer(ip)
    local config = LoadResourceFile(GetCurrentResourceName(), "banlist.json")
    local cfg = json.decode(config)
    cfg[tostring(ip)] = nil;
    SaveResourceFile(GetCurrentResourceName(), "banlist.json", json.encode(cfg, { indent = true }), -1)
end
function GetBans()
    local config = LoadResourceFile(GetCurrentResourceName(), "banlist.json")
    local cfg = json.decode(config)
    return cfg;
end
Citizen.CreateThread(function()
    while true do
        Wait(10000); -- Every 10 seconds
        local bans = GetBans();
        for _, id in pairs(GetPlayers()) do
            local playerIP = ExtractIdentifiers(id).ip;
            if bans[tostring(playerIP)] ~= nil then
                -- Banned, kick em
                DropPlayer(id, "[RES_Anticheat] " .. bans[tostring(playerIP)]);
            end
        end
    end
end)
function OnPlayerConnecting(name, setKickReason, deferrals)
    deferrals.defer();
    print("[RES_Anticheat] Checking their Ban Data");
    local src = source;
    local playerIP = ExtractIdentifiers(src).ip;
    local bans = GetBans();
    local banned = false;
    if bans[tostring(playerIP)] ~= nil then
        -- They are banned
        local reason = bans[tostring(playerIP)];
        print("[RES_Anticheat] (BANNED PLAYER) Player " .. GetPlayerName(src) .. " tried to join, but was banned for: " .. reason);
        deferrals.done("[RES_Anticheat] " .. reason);
        banned = true;
        CancelEvent();
        return;
    end
    if not banned then
        deferrals.done();
    end
end
AddEventHandler("playerConnecting", OnPlayerConnecting)

local BlacklistedEvents = Events;
for i=1, #BlacklistedEvents, 1 do
  RegisterServerEvent(BlacklistedEvents[i])
    AddEventHandler(BlacklistedEvents[i], function()
        local id = source;
        local ids = ExtractIdentifiers(id);
        local steam = ids.steam:gsub("steam:", "");
        local steamDec = tostring(tonumber(steam,16));
        steam = "https://steamcommunity.com/profiles/" .. steamDec;
        local gameLicense = ids.license;
        local discord = ids.discord;
        if Config.Ban then
            BanPlayer(id, "Lua execution: " .. BlacklistedEvents[i]);
            sendToDisc("[BANNED] CONFIRMED HACKER (Tried executing `".. BlacklistedEvents[i] .."`): _[" .. tostring(id) .. "] " .. GetPlayerName(id) .. "_",
            'Steam: **' .. steam .. '**\n' ..
            'GameLicense: **' .. gameLicense .. '**\n' ..
            'Discord Tag: **<@' .. discord:gsub('discord:', '') .. '>**\n' ..
            'Discord UID: **' .. discord:gsub('discord:', '') .. '**\n');
        else
            sendToDisc("CONFIRMED HACKER [Tried executing `".. BlacklistedEvents[i] .."`]: _[" .. tostring(id) .. "] " .. GetPlayerName(id) .. "_",
            'Steam: **' .. steam .. '**\n' ..
            'GameLicense: **' .. gameLicense .. '**\n' ..
            'Discord Tag: **<@' .. discord:gsub('discord:', '') .. '>**\n' ..
            'Discord UID: **' .. discord:gsub('discord:', '') .. '**\n');
        end
      DropPlayer(id, "Lua execution: "..BlacklistedEvents[i],true)
    end)
end

function sendToDisc(title, message, footer)
    local embed = {}
    embed = {
        {
            ["color"] = 16711680, -- GREEN = 65280 --- RED = 16711680
            ["title"] = "**".. title .."**",
            ["description"] = "" .. message ..  "",
            ["footer"] = {
                ["text"] = footer,
            },
        }
    }
    -- Start
    PerformHttpRequest(webhookURL,
    function(err, text, headers) end, 'POST', json.encode({username = name, embeds = embed}), { ['Content-Type'] = 'application/json' })
  -- END
end

function ExtractIdentifiers(src)
    local identifiers = {
        steam = "",
        ip = "",
        discord = "",
        license = "",
        xbl = "",
        live = ""
    }

    --Loop over all identifiers
    for i = 0, GetNumPlayerIdentifiers(src) - 1 do
        local id = GetPlayerIdentifier(src, i)

        --Convert it to a nice table.
        if string.find(id, "steam") then
            identifiers.steam = id
        elseif string.find(id, "ip") then
            identifiers.ip = id
        elseif string.find(id, "discord") then
            identifiers.discord = id
        elseif string.find(id, "license") then
            identifiers.license = id
        elseif string.find(id, "xbl") then
            identifiers.xbl = id
        elseif string.find(id, "live") then
            identifiers.live = id
        end
    end

    return identifiers
end
