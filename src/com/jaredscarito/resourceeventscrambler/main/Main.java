package com.jaredscarito.resourceeventscrambler.main;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) {
		Path resources = Paths.get(args.length > 0 ? args[0] : "resources");
		Path config = Paths.get(args.length > 1 ? args[1] : "config");
		Path eventsFile = Paths.get(args.length > 2 ? args[2] : "RES_Anticheat/events.lua");
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
