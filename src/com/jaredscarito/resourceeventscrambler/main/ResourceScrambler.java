package com.jaredscarito.resourceeventscrambler.main;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rewrites quoted event-name arguments in FiveM resources and records the
 * original names for RES_Anticheat. Replacement is limited to the argument
 * that belongs to a known call, so a short name cannot corrupt a longer one.
 */
public final class ResourceScrambler {
	private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
	private static final int NAME_LENGTH = 12;
	private static final String NUI_EVENT_PREFIX = "__cfx_nui:";
	private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	private static final Set<String> SKIPPED_DIRECTORIES = new HashSet<String>(Arrays.asList(
			"node_modules", ".git", ".svn", "bower_components"));

	private static final Set<String> LOOSE_SKIP_DIRECTORIES = new HashSet<String>(Arrays.asList(
			"dist", "build", "node_modules"));

	private static final Set<String> DOM_AND_NODE_EVENTS = new HashSet<String>(Arrays.asList(
			"click", "dblclick", "mousedown", "mouseup", "mousemove", "mouseover", "mouseout",
			"mouseenter", "mouseleave", "contextmenu", "wheel", "scroll", "keydown", "keyup",
			"keypress", "input", "change", "submit", "focus", "blur", "focusin", "focusout",
			"load", "unload", "error", "resize", "drag", "dragstart", "dragend", "dragover",
			"dragenter", "dragleave", "drop", "pointerdown", "pointerup", "pointermove",
			"touchstart", "touchend", "touchmove", "animationend", "animationstart",
			"transitionend", "message", "hashchange", "popstate", "storage", "online",
			"offline", "visibilitychange", "beforeunload", "domcontentloaded", "readystatechange",
			"abort", "canplay", "play", "pause", "ended", "volumechange", "timeupdate", "seeking",
			"seeked", "loadeddata", "loadedmetadata", "progress", "durationchange", "ratechange",
			"suspend", "emptied", "stalled", "waiting", "copy", "cut", "paste", "reset", "search",
			"toggle", "show", "close", "open", "cancel", "invalid", "select", "slotchange",
			"data", "end", "finish", "connect", "connection", "disconnect", "listening",
			"request", "response", "upgrade", "timeout", "readable", "drain", "pipe", "unpipe",
			"lookup"));

	private static final Rule[] RULES = {
			rule("TriggerLatentClientEvent", true, true, false, false),
			rule("TriggerLatentServerEvent", true, true, false, false),
			rule("QBCore.Functions.CreateClientCallback", true, true, false, false),
			rule("QBCore.Functions.TriggerClientCallback", true, true, false, false),
			rule("QBCore.Functions.CreateCallback", true, true, false, false),
			rule("QBCore.Functions.TriggerCallback", true, true, false, false),
			rule("ESX.RegisterServerCallback", true, true, false, false),
			rule("ESX.TriggerServerCallback", true, true, false, false),
			rule("ESX.RegisterClientCallback", true, true, false, false),
			rule("ESX.TriggerClientCallback", true, true, false, false),
			rule("lib.callback.register", true, true, false, false),
			rule("lib.callback.await", true, true, false, false),
			rule("lib.callback", true, true, false, false),
			rule("RegisterRawNuiCallback", true, true, true, false),
			rule("RegisterNuiCallbackType", true, true, true, false),
			rule("RegisterNUICallback", true, true, true, false),
			rule("RegisterNuiCallback", true, true, true, false),
			rule("RegisterServerEvent", true, true, false, false),
			rule("RegisterNetEvent", true, true, false, false),
			rule("TriggerClientEvent", true, true, false, false),
			rule("TriggerServerEvent", true, true, false, false),
			rule("AddEventHandler", true, true, false, false),
			rule("TriggerEvent", true, true, false, false),
			rule("onNet", false, true, false, false),
			rule("emitNet", false, true, false, false),
			rule("emit", false, true, false, true),
			rule("on", false, true, false, true)
	};

	private ResourceScrambler() {
	}

	public static Outcome run(Path resourcesDir, Path configPath, Path eventsFile) throws IOException, ScrambleException {
		System.out.println("[ResourceEventScrambler] Running Scrambler");
		Path resources = resourcesDir.toAbsolutePath().normalize();
		Path config = configPath.toAbsolutePath().normalize();
		Path events = eventsFile.toAbsolutePath().normalize();

		ScramblerConfig loaded = ScramblerConfig.load(config);
		if (!Files.isDirectory(resources)) {
			throw new ScrambleException("Could not find the resources folder at '" + resources
					+ "'. Run this from the folder that contains server.cfg and resources.");
		}

		Path backup = resources.resolveSibling(resources.getFileName().toString() + " [UNSCRAMBLED]");
		if (Files.exists(backup)) {
			throw new ScrambleException("A backup already exists at '" + backup + "'. "
					+ "Refusing to scramble again, because a second run would rename events that are already scrambled "
					+ "and overwrite the clean backup. Restore that folder over '" + resources.getFileName()
					+ "', add any new resources, delete the backup folder, then run the scrambler again.");
		}

		Scan scan = scan(resources, events, loaded);
		assignNames(scan);

		try {
			copyFolder(resources, backup);
		} catch (IOException e) {
			deleteRecursive(backup);
			throw new ScrambleException("Failed to create the backup at '" + backup
					+ "'. No resources were modified. " + e.getMessage(), e);
		}
		System.out.println("[ResourceEventScrambler] Created folder: " + backup);

		int filesChanged = 0;
		try {
			filesChanged = apply(scan);
			writeEvents(events, scan.watched);
		} catch (IOException | RuntimeException e) {
			throw new ScrambleException("Scrambling failed after the backup was created at '" + backup
					+ "'. Restore that folder before trying again. " + e, e);
		}

		List<String> originals = new ArrayList<String>(scan.mapping.keySet());
		Collections.sort(originals);
		for (String original : originals) {
			System.out.println("[ResourceEventScrambler] '" + original + "' -> '" + scan.mapping.get(original) + "'");
		}
		System.out.println("[ResourceEventScrambler] Wrote anticheat event list (" + scan.watched.size() + " events)");
		System.out.println("[ResourceEventScrambler] SCRAMBLING COMPLETED (" + scan.mapping.size()
				+ " unique names, " + filesChanged + " files)");
		return new Outcome(filesChanged, scan.mapping, scan.watched);
	}

	private static Scan scan(Path resources, Path eventsFile, ScramblerConfig config) throws IOException, ScrambleException {
		Scan scan = new Scan(config);
		final IOException[] failure = new IOException[1];
		final ScrambleException[] rejected = new ScrambleException[1];

		Files.walkFileTree(resources, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (dir.equals(resources)) {
					return FileVisitResult.CONTINUE;
				}
				String name = dir.getFileName().toString();
				if (SKIPPED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (config.isBlacklistedResource(name)) {
					System.out.println("[ResourceEventScrambler] Skipped blacklisted resource: " + name);
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				try {
					consider(resources, eventsFile, file, scan);
				} catch (IOException e) {
					failure[0] = e;
					return FileVisitResult.TERMINATE;
				} catch (ScrambleException e) {
					rejected[0] = e;
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				failure[0] = exc;
				return FileVisitResult.TERMINATE;
			}
		});

		if (rejected[0] != null) {
			throw rejected[0];
		}
		if (failure[0] != null) {
			throw failure[0];
		}
		return scan;
	}

	private static void consider(Path resources, Path eventsFile, Path file, Scan scan) throws IOException, ScrambleException {
		Path absolute = file.toAbsolutePath().normalize();
		if (absolute.equals(eventsFile)) {
			return;
		}
		Language language = languageOf(file);
		if (language == null) {
			return;
		}
		String relative = resources.relativize(absolute).toString().replace('\\', '/');
		if (scan.config.isBlacklistedFile(relative)) {
			System.out.println("[ResourceEventScrambler] Skipped blacklisted file: " + relative);
			return;
		}

		byte[] bytes = Files.readAllBytes(absolute);
		if (bytes.length > MAX_FILE_BYTES) {
			throw new ScrambleException("Refusing to edit '" + relative + "' because it is larger than "
					+ (MAX_FILE_BYTES / (1024 * 1024)) + " MB. Add it under Blacklisted Files to leave it untouched.");
		}
		if (looksBinary(bytes)) {
			throw new ScrambleException("Refusing to edit '" + relative
					+ "' because it looks binary. Add it under Blacklisted Files to leave it untouched.");
		}

		Decoded decoded = decode(bytes);
		boolean[] ignored = ignoreMask(decoded.text, language);
		Map<Integer, Edit> edits = new HashMap<Integer, Edit>();
		for (Rule rule : RULES) {
			if (!rule.appliesTo(language)) {
				continue;
			}
			collectCalls(decoded.text, ignored, rule, file, edits, scan);
		}

		scan.files.add(new PlannedFile(absolute, relative,
				edits.isEmpty() ? Collections.<Edit>emptyList() : new ArrayList<Edit>(edits.values())));
	}

	private static void collectCalls(String content, boolean[] ignored, Rule rule, Path file, Map<Integer, Edit> edits, Scan scan) {
		int from = 0;
		while (from < content.length()) {
			int found = indexOfFunction(content, rule.name, from);
			if (found < 0) {
				return;
			}
			if (ignored[found]) {
				from = found + rule.name.length();
				continue;
			}
			int cursor = found + rule.name.length();
			while (cursor < content.length() && isWhitespace(content.charAt(cursor))) {
				cursor++;
			}
			if (cursor >= content.length() || content.charAt(cursor) != '(') {
				from = found + 1;
				continue;
			}
			cursor++;
			while (cursor < content.length() && isWhitespace(content.charAt(cursor))) {
				cursor++;
			}
			Quoted quoted = parseQuoted(content, cursor);
			if (quoted == null || !acceptableName(quoted.name)) {
				from = found + rule.name.length();
				continue;
			}
			record(quoted, rule, file, edits, scan);
			from = quoted.end;
		}
	}

	private static void record(Quoted quoted, Rule rule, Path file, Map<Integer, Edit> edits, Scan scan) {
		if (edits.containsKey(quoted.start)) {
			return;
		}
		String name = quoted.name;
		if (rule.loose && isLooseSkipped(file) && !name.startsWith(NUI_EVENT_PREFIX)) {
			return;
		}
		if (rule.loose && DOM_AND_NODE_EVENTS.contains(name.toLowerCase(Locale.ROOT)) && !name.startsWith(NUI_EVENT_PREFIX)) {
			return;
		}

		String key = name;
		String prefix = "";
		if (name.startsWith(NUI_EVENT_PREFIX)) {
			key = name.substring(NUI_EVENT_PREFIX.length());
			prefix = NUI_EVENT_PREFIX;
			if (!acceptableName(key)) {
				return;
			}
		}
		if (scan.config.isBlacklistedEvent(name) || scan.config.isBlacklistedEvent(key)) {
			return;
		}

		edits.put(quoted.start, new Edit(quoted.start, quoted.end, quoted.quote, key, prefix));
		scan.keys.add(key);
		scan.watched.add(name);
		if (rule.nui || !prefix.isEmpty()) {
			scan.nuiNames.add(key);
		}
	}

	private static void assignNames(Scan scan) {
		Set<String> used = new HashSet<String>();
		SecureRandom random = new SecureRandom();
		List<String> keys = new ArrayList<String>(scan.keys);
		Collections.sort(keys);
		for (String key : keys) {
			String generated = uniqueName(random, scan.keys, used);
			scan.mapping.put(key, generated);
			used.add(generated);
		}
	}

	private static String uniqueName(SecureRandom random, Set<String> reserved, Set<String> used) {
		while (true) {
			StringBuilder builder = new StringBuilder(NAME_LENGTH);
			for (int i = 0; i < NAME_LENGTH; i++) {
				builder.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
			}
			String name = builder.toString();
			if (!reserved.contains(name) && !used.contains(name)) {
				used.add(name);
				return name;
			}
		}
	}

	private static int apply(Scan scan) throws IOException {
		int filesChanged = 0;
		Set<Path> written = new HashSet<Path>();
		for (PlannedFile planned : scan.files) {
			if (planned.edits.isEmpty() && scan.nuiNames.isEmpty()) {
				continue;
			}
			byte[] bytes = Files.readAllBytes(planned.path);
			Decoded decoded = decode(bytes);
			String updated = applyEdits(decoded.text, planned.edits, scan.mapping);
			if (!scan.nuiNames.isEmpty()) {
				updated = applyNuiPaths(updated, scan);
			}
			if (updated.equals(decoded.text)) {
				continue;
			}
			writeText(planned.path, updated, decoded);
			written.add(planned.path);
			filesChanged++;
			System.out.println("[ResourceEventScrambler] Rewrote file: " + planned.relative);
		}
		return written.size();
	}

	private static String applyEdits(String content, List<Edit> edits, Map<String, String> mapping) {
		if (edits.isEmpty()) {
			return content;
		}
		List<Edit> ordered = new ArrayList<Edit>(edits);
		Collections.sort(ordered, new Comparator<Edit>() {
			@Override
			public int compare(Edit left, Edit right) {
				return Integer.compare(left.start, right.start);
			}
		});
		StringBuilder builder = new StringBuilder(content.length());
		int cursor = 0;
		for (Edit edit : ordered) {
			if (edit.start < cursor || edit.end > content.length() || edit.start > edit.end) {
				continue;
			}
			String scrambled = mapping.get(edit.key);
			if (scrambled == null) {
				continue;
			}
			builder.append(content, cursor, edit.start);
			builder.append(edit.quote).append(edit.prefix).append(scrambled).append(edit.quote);
			cursor = edit.end;
		}
		builder.append(content, cursor, content.length());
		return builder.toString();
	}

	private static String applyNuiPaths(String content, Scan scan) {
		List<String> names = new ArrayList<String>(scan.nuiNames);
		Collections.sort(names, new Comparator<String>() {
			@Override
			public int compare(String left, String right) {
				return Integer.compare(right.length(), left.length());
			}
		});
		for (String oldName : names) {
			String scrambled = scan.mapping.get(oldName);
			if (scrambled == null) {
				continue;
			}
			String needle = "/" + oldName;
			StringBuilder builder = new StringBuilder(content.length());
			int cursor = 0;
			int from = 0;
			while (from < content.length()) {
				int index = content.indexOf(needle, from);
				if (index < 0) {
					break;
				}
				int after = index + needle.length();
				if (!nuiBoundary(content, after) || !hasNuiContext(content, index)) {
					from = index + 1;
					continue;
				}
				builder.append(content, cursor, index);
				builder.append('/').append(scrambled);
				cursor = after;
				from = after;
			}
			builder.append(content, cursor, content.length());
			content = builder.toString();
		}
		return content;
	}

	private static boolean nuiBoundary(String content, int after) {
		if (after >= content.length()) {
			return true;
		}
		char next = content.charAt(after);
		if (next == '.') {
			return false;
		}
		return !Character.isLetterOrDigit(next) && next != '_';
	}

	private static boolean hasNuiContext(String content, int slashIndex) {
		int from = Math.max(0, slashIndex - 160);
		String window = content.substring(from, slashIndex).toLowerCase(Locale.ROOT);
		return window.contains("https://")
				|| window.contains("http://")
				|| window.contains("nui://")
				|| window.contains("getparentresourcename")
				|| window.contains("cfx-nui");
	}

	private static void writeEvents(Path eventsFile, Set<String> watched) throws IOException {
		Path parent = eventsFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		StringBuilder builder = new StringBuilder();
		builder.append("Events = {\n");
		for (String eventName : watched) {
			builder.append(luaString(eventName)).append(",\n");
		}
		builder.append("}\n");
		Files.write(eventsFile, builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String luaString(String value) {
		StringBuilder builder = new StringBuilder(value.length() + 2);
		builder.append('\'');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\':
					builder.append("\\\\");
					break;
				case '\'':
					builder.append("\\'");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				default:
					builder.append(c);
					break;
			}
		}
		builder.append('\'');
		return builder.toString();
	}

	private static int indexOfFunction(String content, String name, int from) {
		int i = from;
		while (i <= content.length() - name.length()) {
			int found = content.indexOf(name, i);
			if (found < 0) {
				return -1;
			}
			if (hasBoundary(content, found, name.length())) {
				return found;
			}
			i = found + 1;
		}
		return -1;
	}

	private static boolean hasBoundary(String content, int start, int length) {
		if (start > 0) {
			char before = content.charAt(start - 1);
			if (Character.isLetterOrDigit(before) || before == '_' || before == '.') {
				return false;
			}
		}
		int after = start + length;
		if (after < content.length()) {
			char next = content.charAt(after);
			if (Character.isLetterOrDigit(next) || next == '_' || next == '.') {
				return false;
			}
		}
		return true;
	}

	private static Quoted parseQuoted(String content, int index) {
		if (index >= content.length()) {
			return null;
		}
		char quote = content.charAt(index);
		if (quote == '\'' || quote == '"' || quote == '`') {
			StringBuilder value = new StringBuilder();
			int cursor = index + 1;
			while (cursor < content.length()) {
				char current = content.charAt(cursor);
				if (current == '\\' && cursor + 1 < content.length()) {
					value.append(content.charAt(cursor + 1));
					cursor += 2;
					continue;
				}
				if (current == quote) {
					return new Quoted(index, cursor + 1, value.toString(), quote);
				}
				if (current == '\n' || current == '\r') {
					return null;
				}
				value.append(current);
				cursor++;
			}
			return null;
		}
		if (quote == '[') {
			return parseLongString(content, index);
		}
		return null;
	}

	private static Quoted parseLongString(String content, int index) {
		int equals = 0;
		int cursor = index + 1;
		while (cursor < content.length() && content.charAt(cursor) == '=') {
			equals++;
			cursor++;
		}
		if (cursor >= content.length() || content.charAt(cursor) != '[') {
			return null;
		}
		String close = "]" + repeat('=', equals) + "]";
		int end = content.indexOf(close, cursor + 1);
		if (end < 0) {
			return null;
		}
		String value = content.substring(cursor + 1, end);
		if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
			return null;
		}
		return new Quoted(index, end + close.length(), value, '"');
	}

	private static int longBracketEnd(String content, int openBracket) {
		int equals = 0;
		int cursor = openBracket + 1;
		while (cursor < content.length() && content.charAt(cursor) == '=') {
			equals++;
			cursor++;
		}
		if (cursor >= content.length() || content.charAt(cursor) != '[') {
			return -1;
		}
		String close = "]" + repeat('=', equals) + "]";
		int end = content.indexOf(close, cursor + 1);
		if (end < 0) {
			return content.length();
		}
		return end + close.length();
	}

	private static boolean[] ignoreMask(String content, Language language) {
		boolean[] mask = new boolean[content.length()];
		int i = 0;
		while (i < content.length()) {
			char c = content.charAt(i);
			if (language == Language.LUA) {
				if (c == '-' && i + 1 < content.length() && content.charAt(i + 1) == '-') {
					int end;
					if (i + 2 < content.length() && content.charAt(i + 2) == '[') {
						int longEnd = longBracketEnd(content, i + 2);
						end = longEnd >= 0 ? longEnd : lineEnd(content, i);
					} else {
						end = lineEnd(content, i);
					}
					fill(mask, i, end);
					i = end;
					continue;
				}
				if (c == '\'' || c == '"') {
					i = fillString(content, mask, i);
					continue;
				}
				if (c == '[') {
					int end = longBracketEnd(content, i);
					if (end >= 0) {
						fill(mask, i, end);
						i = end;
						continue;
					}
				}
				i++;
				continue;
			}

			if ((language == Language.HTML) && c == '<' && startsWith(content, i, "<!--")) {
				int end = content.indexOf("-->", i + 4);
				end = end < 0 ? content.length() : end + 3;
				fill(mask, i, end);
				i = end;
				continue;
			}
			if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
				int end = lineEnd(content, i);
				fill(mask, i, end);
				i = end;
				continue;
			}
			if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '*') {
				int end = content.indexOf("*/", i + 2);
				end = end < 0 ? content.length() : end + 2;
				fill(mask, i, end);
				i = end;
				continue;
			}
			if (c == '\'' || c == '"') {
				i = fillString(content, mask, i);
				continue;
			}
			if (c == '`') {
				i = skipTemplate(content, i);
				continue;
			}
			i++;
		}
		return mask;
	}

	private static int fillString(String content, boolean[] mask, int index) {
		char quote = content.charAt(index);
		mask[index] = true;
		int i = index + 1;
		while (i < content.length()) {
			mask[i] = true;
			char c = content.charAt(i);
			if (c == '\\') {
				if (i + 1 < content.length()) {
					mask[i + 1] = true;
				}
				i += 2;
				continue;
			}
			if (c == quote || c == '\n') {
				return i + 1;
			}
			i++;
		}
		return i;
	}

	private static int skipTemplate(String content, int index) {
		int i = index + 1;
		while (i < content.length()) {
			char c = content.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '`') {
				return i + 1;
			}
			i++;
		}
		return content.length();
	}

	private static int lineEnd(String content, int index) {
		int end = content.indexOf('\n', index);
		return end < 0 ? content.length() : end;
	}

	private static void fill(boolean[] mask, int from, int to) {
		int end = Math.min(to, mask.length);
		for (int i = from; i < end; i++) {
			mask[i] = true;
		}
	}

	private static boolean startsWith(String content, int index, String prefix) {
		return content.regionMatches(index, prefix, 0, prefix.length());
	}

	private static boolean acceptableName(String name) {
		if (name == null || name.isEmpty() || name.length() > 200) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '\n' || c == '\r' || c == '\0') {
				return false;
			}
		}
		return true;
	}

	private static boolean isWhitespace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
	}

	private static boolean isLooseSkipped(Path file) {
		for (Path part : file) {
			if (LOOSE_SKIP_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return fileName.endsWith(".min.js") || fileName.endsWith(".min.mjs");
	}

	private static boolean looksBinary(byte[] bytes) {
		int length = Math.min(bytes.length, 8000);
		for (int i = 0; i < length; i++) {
			if (bytes[i] == 0) {
				return true;
			}
		}
		return false;
	}

	private static Language languageOf(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".lua")) {
			return Language.LUA;
		}
		if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".vue") || name.endsWith(".svelte")) {
			return Language.HTML;
		}
		if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs") || name.endsWith(".cjs")
				|| name.endsWith(".ts") || name.endsWith(".tsx")) {
			return Language.JS;
		}
		return null;
	}

	private static Decoded decode(byte[] bytes) {
		int offset = 0;
		boolean bom = bytes.length >= 3
				&& (bytes[0] & 0xFF) == 0xEF
				&& (bytes[1] & 0xFF) == 0xBB
				&& (bytes[2] & 0xFF) == 0xBF;
		if (bom) {
			offset = 3;
		}
		byte[] body = offset == 0 ? bytes : Arrays.copyOfRange(bytes, offset, bytes.length);
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try {
			String text = decoder.decode(ByteBuffer.wrap(body)).toString();
			return new Decoded(text, StandardCharsets.UTF_8, bom);
		} catch (CharacterCodingException e) {
			return new Decoded(new String(body, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1, bom);
		}
	}

	private static void writeText(Path path, String text, Decoded original) throws IOException {
		byte[] body = text.getBytes(original.charset);
		if (!original.bom) {
			Files.write(path, body);
			return;
		}
		byte[] withBom = new byte[body.length + 3];
		withBom[0] = (byte) 0xEF;
		withBom[1] = (byte) 0xBB;
		withBom[2] = (byte) 0xBF;
		System.arraycopy(body, 0, withBom, 3, body.length);
		Files.write(path, withBom);
	}

	private static void deleteRecursive(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try {
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
		} catch (IOException e) {
			System.out.println("[ResourceEventScrambler] Could not remove incomplete backup '" + root + "': " + e.getMessage());
		}
	}

	private static void copyFolder(Path source, Path destination) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(destination.resolve(source.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path target = destination.resolve(source.relativize(file));
				try {
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				} catch (IOException attributeFailure) {
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static String repeat(char c, int count) {
		char[] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}

	private static Rule rule(String name, boolean lua, boolean script, boolean nui, boolean loose) {
		return new Rule(name, lua, script, nui, loose);
	}

	public static final class Outcome {
		public final int filesChanged;
		public final Map<String, String> mapping;
		public final Set<String> watched;

		Outcome(int filesChanged, Map<String, String> mapping, Set<String> watched) {
			this.filesChanged = filesChanged;
			this.mapping = Collections.unmodifiableMap(new LinkedHashMap<String, String>(mapping));
			this.watched = Collections.unmodifiableSet(new LinkedHashSet<String>(watched));
		}
	}

	private enum Language {
		LUA, JS, HTML
	}

	private static final class Rule {
		final String name;
		final boolean lua;
		final boolean script;
		final boolean nui;
		final boolean loose;

		Rule(String name, boolean lua, boolean script, boolean nui, boolean loose) {
			this.name = name;
			this.lua = lua;
			this.script = script;
			this.nui = nui;
			this.loose = loose;
		}

		boolean appliesTo(Language language) {
			if (language == Language.LUA) {
				return lua;
			}
			return script;
		}
	}

	private static final class Quoted {
		final int start;
		final int end;
		final String name;
		final char quote;

		Quoted(int start, int end, String name, char quote) {
			this.start = start;
			this.end = end;
			this.name = name;
			this.quote = quote;
		}
	}

	private static final class Edit {
		final int start;
		final int end;
		final char quote;
		final String key;
		final String prefix;

		Edit(int start, int end, char quote, String key, String prefix) {
			this.start = start;
			this.end = end;
			this.quote = quote;
			this.key = key;
			this.prefix = prefix;
		}
	}

	private static final class PlannedFile {
		final Path path;
		final String relative;
		final List<Edit> edits;

		PlannedFile(Path path, String relative, List<Edit> edits) {
			this.path = path;
			this.relative = relative;
			this.edits = edits;
		}
	}

	private static final class Decoded {
		final String text;
		final Charset charset;
		final boolean bom;

		Decoded(String text, Charset charset, boolean bom) {
			this.text = text;
			this.charset = charset;
			this.bom = bom;
		}
	}

	private static final class Scan {
		final ScramblerConfig config;
		final List<PlannedFile> files = new ArrayList<PlannedFile>();
		final Set<String> keys = new LinkedHashSet<String>();
		final Set<String> watched = new TreeSet<String>();
		final Set<String> nuiNames = new LinkedHashSet<String>();
		final Map<String, String> mapping = new LinkedHashMap<String, String>();

		Scan(ScramblerConfig config) {
			this.config = config;
		}
	}
}
