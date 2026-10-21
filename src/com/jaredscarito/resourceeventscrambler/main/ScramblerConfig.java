package com.jaredscarito.resourceeventscrambler.main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class ScramblerConfig {
	private static final String ALWAYS_SKIPPED_RESOURCE = "RES_Anticheat";

	final Set<String> blacklistedResources;
	final Set<String> blacklistedEvents;
	final List<String> blacklistedFiles;

	private ScramblerConfig(Set<String> blacklistedResources, Set<String> blacklistedEvents, List<String> blacklistedFiles) {
		this.blacklistedResources = blacklistedResources;
		this.blacklistedEvents = blacklistedEvents;
		this.blacklistedFiles = blacklistedFiles;
	}

	static ScramblerConfig load(Path configPath) throws IOException {
		if (!Files.exists(configPath)) {
			System.out.println("[ResourceEventScrambler] No config detected. Creating " + configPath.toAbsolutePath());
			writeDefault(configPath);
		}
		return parse(Files.readAllLines(configPath, StandardCharsets.UTF_8));
	}

	static ScramblerConfig defaultConfig() {
		return parse(Arrays.asList(DEFAULT_LINES));
	}

	static void save(Path configPath, Iterable<String> resources, Iterable<String> events, Iterable<String> files) throws IOException {
		List<String> lines = new ArrayList<String>();
		lines.add("#Add More Lines as needed.");
		lines.add("Blacklisted Resources:");
		lines.add("# (These Resources will NOT be modified by the scrambler)");
		for (String resource : resources) {
			String trimmed = resource.trim();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
				lines.add(trimmed);
			}
		}
		lines.add("");
		lines.add("Blacklisted Events:");
		for (String event : events) {
			String trimmed = event.trim();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
				lines.add(trimmed);
			}
		}
		lines.add("");
		lines.add("Blacklisted Files:");
		lines.add("# These files will not be modified. Use the path relative to the resources folder, without a leading slash.");
		for (String file : files) {
			String trimmed = file.trim();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
				lines.add(normalizeRelative(trimmed));
			}
		}
		lines.add("");
		Path parent = configPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(configPath, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
	}

	private static ScramblerConfig parse(List<String> sourceLines) {
		Set<String> resources = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		Set<String> events = new HashSet<String>();
		List<String> files = new ArrayList<String>();
		int section = -1;
		for (String line : sourceLines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if (trimmed.equals("Blacklisted Resources:")) {
				section = 0;
				continue;
			}
			if (trimmed.equals("Blacklisted Events:")) {
				section = 1;
				continue;
			}
			if (trimmed.equals("Blacklisted Files:")) {
				section = 2;
				continue;
			}
			switch (section) {
				case 0:
					resources.add(trimmed);
					break;
				case 1:
					events.add(trimmed);
					break;
				case 2:
					files.add(normalizeRelative(trimmed));
					break;
				default:
					break;
			}
		}
		resources.add(ALWAYS_SKIPPED_RESOURCE);
		return new ScramblerConfig(
				Collections.unmodifiableSet(resources),
				Collections.unmodifiableSet(events),
				Collections.unmodifiableList(files));
	}

	boolean isBlacklistedResource(String directoryName) {
		return blacklistedResources.contains(directoryName);
	}

	boolean isBlacklistedEvent(String eventName) {
		return blacklistedEvents.contains(eventName);
	}

	boolean isBlacklistedFile(String relativePath) {
		String normalized = normalizeRelative(relativePath);
		for (String blocked : blacklistedFiles) {
			if (blocked.equalsIgnoreCase(normalized)) {
				return true;
			}
		}
		return false;
	}

	private static String normalizeRelative(String path) {
		String normalized = path.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static void writeDefault(Path configPath) throws IOException {
		Path parent = configPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(configPath, String.join("\n", DEFAULT_LINES).getBytes(StandardCharsets.UTF_8));
	}

	private static final String[] DEFAULT_LINES = {
			"#Add More Lines as needed.",
			"Blacklisted Resources:",
			"# (These Resources will NOT be modified by the scrambler)",
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
			"mysql-async",
			"RES_Anticheat",
			"",
			"Blacklisted Events:",
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
			"onResourceStop",
			"",
			"Blacklisted Files:",
			"# These files will not be modified. Use the path relative to the resources folder, without a leading slash.",
			""
	};
}
