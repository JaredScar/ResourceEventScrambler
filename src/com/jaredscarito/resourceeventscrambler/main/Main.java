package com.jaredscarito.resourceeventscrambler.main;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static String[] systemResources = {
            // FiveM
            "fivem",
            "fivem-awesome1501",
            "fivem-map-hipster",
            "fivem-map-skater",
            "runcode",
            "race",
            "race-test",
            "channelfeed",
            "irc",
            "obituary",
            "obituary-deaths",
            "playernames",
            "mapmanager",
            "baseevents",
            "chat",
            "hardcap",
            "rconlog",
            "scoreboard",
            "sessionmanager",
            "spawnmanager",
            "yarn",
            "betaguns",
            "gameInit",
            "keks",

            // Vendor
            "mysql-async",
    };
    private static String[] fivemEvents = {
            "playerConnecting",
            "playerSpawned",
            "playerDropped",
            "onResourceListRefresh",
            "weaponDamageEvent",
            "entityRemoved",
            "entityCreating",
            "entityCreated",
            "respawnPlayerPedEvent",
            "explosionEvent",
            "vehicleComponentControlEvent",
            "playerEnteredScope",
            "playerLeftScope",
            "onClientResourceStart",
            "onClientResourceStop",
            "populationPedCreating",
            "onClientMapStart",
            "onClientGameTypeStart",
            "onClientMapStop",
            "onClientGameTypeStop",
            "getMapDirectives",
            "onPlayerDied",
            "onPlayerKilled",
            "baseevents:onPlayerDied",
            "baseevents:onPlayerKilled",
            "playerActivated",
            "sessionInitialized",
            "chatMessage",
            "chat:addMessage",
            "chat:addTemplate",
            "chat:addSuggestion",
            "chat:removeSuggestion",
            "chat:clear",
            "onResourceStart",
            "onResourceStarting",
            "onResourceStop"
    };
    private static boolean isFivemEvent(String eventName) {
        for (String fivemEvent : fivemEvents) {
            if (eventName.equals(fivemEvent)) {
                return true;
            }
        }
        return false;
    }
    private static boolean isSystemResource(String fileName) {
        for (String systemResource : systemResources) {
            if (fileName.equals(systemResource)) {
                return true;
            }
        }
        return false;
    }
    private static HashMap<String, String> keyVals = new HashMap<>();
    public static void main(String[] args) {
        // Main thread
        System.out.println("[ResourceEventScrambler] Running Scrambler");
        List<File> resources = listf("resources");
        File resourceDir = new File("resources");
        copyFolder(resourceDir, new File("resources [UNSCRAMBLED]"));
        System.out.println("[ResourceEventScrambler] Created folder: resources [UNSCRAMBLED]");
        for (File f : resources) {
            if (f.getName().toLowerCase().contains(".lua")) {
                // It's a Lua file
                if (!f.getName().contains("__resource")) {
                    // Not the resource lua, we can scramble the events of this file
                    // Searches:
                    /**
                     this.addEventHandlerRe           = /AddEventHandler\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.triggerEventRe              = /TriggerEvent\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.registerServerEventRe       = /RegisterServerEvent\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.triggerClientEventRe        = /TriggerClientEvent\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.triggerServerEventRe        = /TriggerServerEvent\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.registerNetEventRe          = /RegisterNetEvent\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.esxRegisterServerCallbackRe = /ESX\.RegisterServerCallback\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     this.esxTriggerServerCallbackRe  = /ESX\.TriggerServerCallback\((\n["'](.*?)["']|\n\s+["'](.*?)["']|.+["'](.*?)["']|["'](.*?)["'])/g;
                     */
                    Pattern eventPatt = Pattern.compile("AddEventHandler\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern triggerEventPatt = Pattern.compile("TriggerEvent\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern registerServerEventPatt = Pattern.compile("RegisterServerEvent\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern triggerClientEventPatt = Pattern.compile("TriggerClientEvent\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern triggerServerEventPatt = Pattern.compile("TriggerServerEvent\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern registerNetEventPatt = Pattern.compile("RegisterNetEvent\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern esxRegisterServerCBPatt = Pattern.compile("ESX\\.RegisterServerCallback\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern esxTriggerServerCBPatt = Pattern.compile("ESX\\.TriggerServerCallback\\((\\n[\"'](.*?)[\"']|\\n\\s+[\"'](.*?)[\"']|.+[\"'](.*?)[\"']|[\"'](.*?)[\"'])", Pattern.MULTILINE);
                    Pattern[] patterns = {eventPatt, triggerClientEventPatt, triggerEventPatt, registerNetEventPatt, registerServerEventPatt, triggerServerEventPatt,
                    esxRegisterServerCBPatt, esxTriggerServerCBPatt};
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new FileReader(f.getAbsolutePath()));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    try {
                        StringBuilder stringBuilder = new StringBuilder();
                        char[] buffer = new char[1];
                        while (reader.read(buffer) != -1) {
                            stringBuilder.append(new String(buffer));
                            buffer = new char[1];
                        }
                        reader.close();

                        String content = stringBuilder.toString();
                        for (Pattern patt : patterns) {
                            Matcher match = patt.matcher(content);
                            while (match.find()) {
                                String eventName = match.group(5);
                                if (eventName !=null) {
                                    String newEventName = getRandomEventScramble();
                                    if (!isFivemEvent(eventName) && !alreadyExists(eventName)) {
                                        if (!keyVals.containsKey(eventName)) {
                                            content = content.replaceAll("'" + eventName + "'", "'" + newEventName + "'");
                                            content = content.replaceAll("\"" + eventName + "\"", "\"" + newEventName + "\"");
                                            keyVals.put(eventName, newEventName);
                                            System.out.println("[ResourceEventScrambler] Replaced event named '" + eventName + "' with: " + newEventName);
                                        } else {
                                            newEventName = keyVals.get(eventName);
                                            content = content.replaceAll("'" + eventName + "'", "'" + newEventName + "'");
                                            content = content.replaceAll("\"" + eventName + "\"", "\"" + newEventName + "\"");
                                            System.out.println("[ResourceEventScrambler] Replaced event named '" + eventName + "' with: " + keyVals.get(eventName));
                                        }
                                    }
                                }
                            }
                        }
                        File file = f.getAbsoluteFile();
                        FileOutputStream fooStream = new FileOutputStream(file, false); // true to append
                        // false to overwrite.
                        byte[] bytes = content.getBytes();
                        fooStream.write(bytes);
                        fooStream.close();
                        System.out.println("[ResourceEventScrambler] Rewrote file: " + f.getName());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        for (File f : resources) {
            if (f.getName().toLowerCase().contains(".lua")) {
                // It's a Lua file
                if (!f.getName().contains("__resource")) {
                    // Replace all the events we have tracked
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new FileReader(f.getAbsolutePath()));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    try {
                        StringBuilder stringBuilder = new StringBuilder();
                        char[] buffer = new char[1];
                        while (reader.read(buffer) != -1) {
                            stringBuilder.append(new String(buffer));
                            buffer = new char[1];
                        }
                        reader.close();

                        String content = stringBuilder.toString();
                        for (String eventKey : keyVals.keySet()) {
                            content = content.replaceAll("'" + eventKey + "'", "'" + keyVals.get(eventKey) + "'");
                            content = content.replaceAll("\"" + eventKey + "\"", "\"" + keyVals.get(eventKey) + "\"");
                        }
                        File file = f.getAbsoluteFile();
                        FileOutputStream fooStream = new FileOutputStream(file, false); // true to append
                        // false to overwrite.
                        byte[] bytes = content.getBytes();
                        fooStream.write(bytes);
                        fooStream.close();
                        System.out.println("[ResourceEventScrambler] Updated file with latest events: " + f.getName());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        System.out.println("[ResourceEventScrambler] SCRAMBLING COMPLETED");
    }
    public static boolean alreadyExists(String val) {
        for (String eventName : keyVals.values()) {
            if (val.equals(eventName)) {
                return true;
            }
        }
        return false;
    }
    private static Random rand = new Random();
    public static String getRandomEventScramble() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String newEventName = "";
        boolean found = false;
        while (!found) {
            for (int i = 0; i < 9; i++) {
                newEventName += letters.charAt(rand.nextInt(letters.length()));
            }
            if (!alreadyExists(newEventName)) {
                found = true;
            } else {
                newEventName = "";
            }
        }
        return newEventName;
    }
    public static List<File> listf(String directoryName) {
        File directory = new File(directoryName);

        List<File> resultList = new ArrayList<File>();

        // get all the files from a directory
        File[] fList = directory.listFiles();
        resultList.addAll(Arrays.asList(fList));
        for (File file : fList) {
            if (file.isFile()) {
                //System.out.println(file.getAbsolutePath());
            } else if (file.isDirectory()) {
                if (!isSystemResource(file.getName())) {
                    resultList.addAll(listf(file.getAbsolutePath()));
                }
            }
        }
        //System.out.println(fList);
        return resultList;
    }
    public static void copyFolder(File source, File destination)
    {
        if (source.isDirectory())
        {
            if (!destination.exists())
            {
                destination.mkdirs();
            }

            String files[] = source.list();

            for (String file : files)
            {
                File srcFile = new File(source, file);
                File destFile = new File(destination, file);

                copyFolder(srcFile, destFile);
            }
        }
        else
        {
            InputStream in = null;
            OutputStream out = null;

            try
            {
                in = new FileInputStream(source);
                out = new FileOutputStream(destination);

                byte[] buffer = new byte[1024];

                int length;
                while ((length = in.read(buffer)) > 0)
                {
                    out.write(buffer, 0, length);
                }
            }
            catch (Exception e)
            {
                try
                {
                    in.close();
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }

                try
                {
                    out.close();
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }
            }
        }
    }
}