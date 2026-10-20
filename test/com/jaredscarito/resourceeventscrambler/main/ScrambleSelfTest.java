package com.jaredscarito.resourceeventscrambler.main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;

public class ScrambleSelfTest {
	public static void main(String[] args) throws Exception {
		try {
			runAll();
			System.out.println("All scrambler checks passed");
		} catch (Throwable failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}

	private static void runAll() throws Exception {
		Path root = Files.createTempDirectory("res-scramble");
		Path missing = Files.createTempDirectory("res-missing");
		try {
			testScramble(root);
			testMissingResources(missing);
		} finally {
			delete(root);
			delete(missing);
		}
	}

	private static void testScramble(Path root) throws Exception {
		Path resources = root.resolve("resources");
		Path config = root.resolve("config");
		Path events = root.resolve("RES_Anticheat").resolve("events.lua");

		String manifest = "fx_version 'cerulean'\ngame 'gta5'\nclient_script 'client.lua'\n";
		String client = ""
				+ "-- TriggerEvent(\"comment:event\")\n"
				+ "local note = \"esx:getData should stay\"\n"
				+ "local short = \"prefix-esx-suffix\"\n"
				+ "local also = \"game stays in this string\"\n"
				+ "TriggerEvent(\"game\")\n"
				+ "TriggerEvent(\"esx\")\n"
				+ "TriggerEvent(\"esx:getData\")\n"
				+ "AddEventHandler( \"spaced:event\", function() end)\n"
				+ "AddEventHandler(\n"
				+ "    'newline:event',\n"
				+ "    function()\n"
				+ "    end\n"
				+ ")\n"
				+ "TriggerEvent(\"playerConnecting\")\n"
				+ "TriggerEvent([[long:event]])\n"
				+ "ESX.RegisterServerCallback(\"esx:callback\", function(source, cb) end)\n"
				+ "QBCore.Functions.CreateCallback(\"qb:callback\", function(source, cb) end)\n"
				+ "lib.callback.register(\"ox:callback\", function(source) end)\n"
				+ "lib.callback('ox:direct', false, function() end)\n"
				+ "TriggerLatentServerEvent(\"latent:evt\", 1000)\n"
				+ "RegisterNUICallback(\"closeMenu\", function(data, cb) cb(\"ok\") end)\n"
				+ "RegisterNUICallback(\"close\", function(data, cb) cb(\"ok\") end)\n"
				+ "RegisterNuiCallbackType(\"getInfo\")\n";
		String server = ""
				+ "TriggerClientEvent(\"esx:getData\", source)\n"
				+ "ESX.TriggerServerCallback('esx:callback', function() end)\n"
				+ "QBCore.Functions.TriggerCallback(\"qb:callback\", function() end)\n"
				+ "lib.callback.await(\"ox:callback\", false)\n"
				+ "TriggerLatentClientEvent(\"latent:evt\", source, 1000)\n";
		String mainJs = ""
				+ "onNet(\"js:event\", () => {})\n"
				+ "emitNet(`js:template`)\n"
				+ "emitNet(\"js:event\")\n"
				+ "emit(\"js:local\")\n"
				+ "on(\"click\", () => {})\n"
				+ "on(\"playerSpawned\", () => {})\n"
				+ "on(\"__cfx_nui:getInfo\", (data, cb) => cb({}))\n";
		String appJs = ""
				+ "on(\"click\", function () {})\n"
				+ "on(\"hud:open\", function () {})\n"
				+ "fetch(`https://${GetParentResourceName()}/closeMenu`)\n"
				+ "fetch(`https://${GetParentResourceName()}/close`)\n"
				+ "fetch(`https://${GetParentResourceName()}/getInfo`)\n"
				+ "$.post(\"https://demo/close\", JSON.stringify({}))\n"
				+ "const icon = \"img/close.png\"\n";
		String bundle = "on(\"custom:event\", function () {})\nonNet(\"js:bundled\", function () {})\n";
		String packageJs = "onNet(\"pkg:event\", function () {})\n";
		String chat = "TriggerEvent(\"chat:shouldStay\")\n";
		String kept = "TriggerEvent(\"skipped:event\")\n";
		byte[] latinPrefix = "TriggerEvent(\"latin:event\") -- ".getBytes(StandardCharsets.US_ASCII);
		byte[] latin = Arrays.copyOf(latinPrefix, latinPrefix.length + 1);
		latin[latinPrefix.length] = (byte) 0xE9;

		write(resources.resolve("demo").resolve("fxmanifest.lua"), manifest);
		write(resources.resolve("demo").resolve("client.lua"), client);
		write(resources.resolve("demo").resolve("server.lua"), server);
		write(resources.resolve("demo").resolve("client").resolve("main.js"), mainJs);
		write(resources.resolve("demo").resolve("html").resolve("app.js"), appJs);
		write(resources.resolve("demo").resolve("html").resolve("dist").resolve("bundle.js"), bundle);
		write(resources.resolve("demo").resolve("node_modules").resolve("lib.js"), packageJs);
		write(resources.resolve("demo").resolve("kept.lua"), kept);
		write(resources.resolve("chat").resolve("client.lua"), chat);
		Files.write(resources.resolve("demo").resolve("latin.lua"), latin);
		write(config, ""
				+ "# comment\n"
				+ "Blacklisted Resources:\n"
				+ "\n"
				+ "# system chat\n"
				+ "chat\n"
				+ "\n"
				+ "Blacklisted Events:\n"
				+ "\n"
				+ "playerConnecting\n"
				+ "\n"
				+ "Blacklisted Files:\n"
				+ "demo\\kept.lua\n");

		ResourceScrambler.Outcome outcome = ResourceScrambler.run(resources, config, events);
		Map<String, String> mapping = outcome.mapping;

		check(mapping.containsKey("esx:getData"), "missing esx:getData");
		check(mapping.containsKey("esx"), "missing esx");
		check(!mapping.get("esx").equals(mapping.get("esx:getData")), "short name collided with longer name");
		check(mapping.get("esx:getData").matches("[A-Za-z]{12}"), "unexpected scramble " + mapping.get("esx:getData"));
		check(mapping.get("ox:callback").equals(scramble(mapping, "ox:callback")), "ox callback missing");
		check(mapping.containsKey("ox:direct"), "lib.callback() call was not found");
		check(!mapping.containsKey("playerConnecting"), "blacklisted event was scrambled");
		check(!mapping.containsKey("comment:event"), "comment was scrambled");
		check(!mapping.containsKey("chat:shouldStay"), "blacklisted resource was scrambled");
		check(!mapping.containsKey("skipped:event"), "blacklisted file was scrambled");
		check(!mapping.containsKey("pkg:event"), "node_modules was scrambled");
		check(!mapping.containsKey("custom:event"), "bundled loose event was scrambled");
		check(!mapping.containsKey("click"), "DOM event was scrambled");
		check(mapping.containsKey("js:bundled"), "onNet inside dist was skipped");
		check(mapping.containsKey("playerSpawned"), "playerSpawned was skipped");
		check(mapping.containsKey("hud:open"), "hud:open was skipped");
		check(outcome.watched.contains("esx:getData"), "watched list missing esx:getData");
		check(outcome.watched.contains("__cfx_nui:getInfo"), "watched list missing nui event");
		check(!outcome.watched.contains("playerConnecting"), "watched list contains a blacklisted event");

		String scrambledClient = read(resources.resolve("demo").resolve("client.lua"));
		check(scrambledClient.contains("esx:getData should stay"), "sentence was rewritten: " + scrambledClient);
		check(scrambledClient.contains("prefix-esx-suffix"), "short name corrupted a longer token: " + scrambledClient);
		check(scrambledClient.contains("game stays in this string"), "game string was rewritten: " + scrambledClient);
		check(scrambledClient.contains("comment:event"), "comment lost its event");
		check(scrambledClient.contains("TriggerEvent(\"playerConnecting\")"), "blacklisted call changed");
		check(!scrambledClient.contains("TriggerEvent(\"game\")"), "game call was not rewritten");
		check(!scrambledClient.contains("TriggerEvent(\"esx:getData\")"), "esx:getData call was not rewritten");
		check(scrambledClient.contains("TriggerEvent(\"" + mapping.get("esx") + "\")"), "esx call missing");
		check(scrambledClient.contains("TriggerEvent(\"" + mapping.get("esx:getData") + "\")"), "esx:getData call missing");
		check(scrambledClient.contains("AddEventHandler( \"" + mapping.get("spaced:event") + "\""), "spaced call missing");
		check(scrambledClient.contains("'" + mapping.get("newline:event") + "'"), "newline call missing");
		check(scrambledClient.contains("\"" + mapping.get("long:event") + "\""), "long-string call missing");
		check(!scrambledClient.contains("[[long:event]]"), "long-string call stayed in place");
		check(scrambledClient.contains("\"" + mapping.get("close") + "\""), "nui callback missing");
		check(scrambledClient.contains("\"" + mapping.get("closeMenu") + "\""), "closeMenu callback missing");

		String scrambledServer = read(resources.resolve("demo").resolve("server.lua"));
		check(scrambledServer.contains("\"" + mapping.get("esx:getData") + "\""), "server did not share esx:getData");
		check(scrambledServer.contains("'" + mapping.get("esx:callback") + "'"), "server callback quote was not preserved");
		check(scrambledServer.contains("\"" + mapping.get("ox:callback") + "\""), "ox callback was not shared");
		check(scrambledServer.contains("\"" + mapping.get("latent:evt") + "\""), "latent event was not shared");

		String scrambledJs = read(resources.resolve("demo").resolve("client").resolve("main.js"));
		check(scrambledJs.contains("\"" + mapping.get("js:event") + "\""), "js:event missing");
		check(scrambledJs.contains("`" + mapping.get("js:template") + "`"), "template call missing");
		check(scrambledJs.contains("on(\"click\""), "click handler changed");
		check(scrambledJs.contains("\"" + mapping.get("playerSpawned") + "\""), "playerSpawned missing");
		check(scrambledJs.contains("\"__cfx_nui:" + mapping.get("getInfo") + "\""), "nui handler did not follow getInfo");

		String scrambledApp = read(resources.resolve("demo").resolve("html").resolve("app.js"));
		check(scrambledApp.contains("on(\"click\""), "ui click handler changed: " + scrambledApp);
		check(scrambledApp.contains("\"" + mapping.get("hud:open") + "\""), "hud:open missing");
		check(scrambledApp.contains("/" + mapping.get("closeMenu")), "closeMenu url missing: " + scrambledApp);
		check(scrambledApp.contains("/" + mapping.get("close")), "close url missing: " + scrambledApp);
		check(scrambledApp.contains("/" + mapping.get("getInfo")), "getInfo url missing: " + scrambledApp);
		check(scrambledApp.contains("img/close.png"), "image path changed: " + scrambledApp);
		check(!scrambledApp.contains("/closeMenu"), "closeMenu url was only partially replaced: " + scrambledApp);
		check(!scrambledApp.contains("/close`"), "close template url stayed: " + scrambledApp);
		check(!scrambledApp.contains("/close\""), "close post url stayed: " + scrambledApp);

		String scrambledBundle = read(resources.resolve("demo").resolve("html").resolve("dist").resolve("bundle.js"));
		check(scrambledBundle.contains("on(\"custom:event\""), "bundled event changed: " + scrambledBundle);
		check(scrambledBundle.contains("\"" + mapping.get("js:bundled") + "\""), "bundled onNet missing");

		check(read(resources.resolve("demo").resolve("fxmanifest.lua")).equals(manifest), "fxmanifest was rewritten");
		check(read(resources.resolve("chat").resolve("client.lua")).equals(chat), "blacklisted resource changed");
		check(read(resources.resolve("demo").resolve("kept.lua")).equals(kept), "blacklisted file changed");
		check(read(resources.resolve("demo").resolve("node_modules").resolve("lib.js")).equals(packageJs), "node_modules changed");

		byte[] scrambledLatin = Files.readAllBytes(resources.resolve("demo").resolve("latin.lua"));
		check(indexOf(scrambledLatin, (byte) 0xE9) >= 0, "latin-1 byte was lost");
		check(!new String(scrambledLatin, StandardCharsets.ISO_8859_1).contains("latin:event"), "latin event stayed");
		check(Arrays.equals(Files.readAllBytes(root.resolve("resources [UNSCRAMBLED]").resolve("demo").resolve("latin.lua")), latin),
				"backup of latin file does not match");
		check(read(root.resolve("resources [UNSCRAMBLED]").resolve("demo").resolve("client.lua")).equals(client),
				"backup of client.lua does not match");

		String eventList = read(events);
		check(eventList.contains("'esx:getData'"), "events.lua missing esx:getData: " + eventList);
		check(eventList.contains("'__cfx_nui:getInfo'"), "events.lua missing nui event: " + eventList);
		check(!eventList.contains("'playerConnecting'"), "events.lua listed a blacklisted event");
		check(outcome.filesChanged >= 6, "expected several rewritten files, got " + outcome.filesChanged);

		String afterFirst = read(resources.resolve("demo").resolve("client.lua"));
		try {
			ResourceScrambler.run(resources, config, events);
			throw new AssertionError("second run was allowed");
		} catch (ScrambleException expected) {
			check(expected.getMessage().toLowerCase().contains("backup"), expected.getMessage());
		}
		check(read(resources.resolve("demo").resolve("client.lua")).equals(afterFirst), "second run modified a file");
	}

	private static void testMissingResources(Path root) throws Exception {
		Path config = root.resolve("config");
		try {
			ResourceScrambler.run(root.resolve("does-not-exist"), config, root.resolve("events.lua"));
			throw new AssertionError("missing resources folder was accepted");
		} catch (ScrambleException expected) {
			check(expected.getMessage().toLowerCase().contains("resources"), expected.getMessage());
		}
		check(Files.exists(config), "default config was not created");
	}

	private static String scramble(Map<String, String> mapping, String name) {
		String value = mapping.get(name);
		check(value != null, "missing mapping for " + name);
		return value;
	}

	private static void write(Path path, String content) throws Exception {
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(Path path) throws Exception {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static int indexOf(byte[] bytes, byte value) {
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] == value) {
				return i;
			}
		}
		return -1;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void delete(Path root) throws Exception {
		if (!Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
