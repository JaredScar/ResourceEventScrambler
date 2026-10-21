package com.jaredscarito.resourceeventscrambler.main;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) {
		if (args.length > 0 && "--unscramble".equals(args[0])) {
			runUnscramble(args);
			return;
		}
		if (args.length == 0) {
			ScramblerApp.launchApp(args);
			return;
		}
		runCli(args);
	}

	private static void runUnscramble(String[] args) {
		Path resources = Paths.get(args.length > 1 ? args[1] : "resources");
		Path config = Paths.get(args.length > 2 ? args[2] : "config");
		Path eventsFile = Paths.get(args.length > 3 ? args[3] : "RES_Anticheat/events.lua");
		Path mapFile = args.length > 4 ? Paths.get(args[4]) : ResourceScrambler.eventMapFile(resources);
		try {
			ResourceScrambler.unscramble(resources, config, eventsFile, mapFile);
		} catch (ScrambleException e) {
			System.out.println("[ResourceEventScrambler] " + e.getMessage());
			if (e.getCause() != null) {
				e.getCause().printStackTrace();
			}
			System.exit(1);
		} catch (Exception e) {
			System.out.println("[ResourceEventScrambler] Failed:");
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void runCli(String[] args) {
		int offset = "--cli".equals(args[0]) ? 1 : 0;
		Path resources = Paths.get(args.length > offset ? args[offset] : "resources");
		Path config = Paths.get(args.length > offset + 1 ? args[offset + 1] : "config");
		Path eventsFile = Paths.get(args.length > offset + 2 ? args[offset + 2] : "RES_Anticheat/events.lua");
		try {
			ResourceScrambler.run(resources, config, eventsFile);
		} catch (ScrambleException e) {
			System.out.println("[ResourceEventScrambler] " + e.getMessage());
			if (e.getCause() != null) {
				e.getCause().printStackTrace();
			}
			System.exit(1);
		} catch (Exception e) {
			System.out.println("[ResourceEventScrambler] Failed:");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
