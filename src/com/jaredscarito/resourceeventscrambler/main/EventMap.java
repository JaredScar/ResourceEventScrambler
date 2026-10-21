package com.jaredscarito.resourceeventscrambler.main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saved original-to-scrambled names. Unscramble reads this file to put the
 * quoted event names and NUI callback paths back.
 */
final class EventMap {
	private static final String HEADER = "# ResourceEventScrambler event map v1";

	final Map<String, String> originalToScrambled;
	final Set<String> nuiOriginals;

	private EventMap(Map<String, String> originalToScrambled, Set<String> nuiOriginals) {
		this.originalToScrambled = originalToScrambled;
		this.nuiOriginals = nuiOriginals;
	}

	static EventMap read(Path path) throws IOException, ScrambleException {
		Map<String, String> mapping = new LinkedHashMap<String, String>();
		Set<String> nui = new LinkedHashSet<String>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			if (line.trim().isEmpty() || line.startsWith("#")) {
				continue;
			}
			List<String> fields = splitFields(line);
			if (fields.size() != 3) {
				throw new ScrambleException("Event map line is not original, scrambled, nui: " + line);
			}
			String original = fields.get(0);
			String scrambled = fields.get(1);
			if (original.isEmpty() || scrambled.isEmpty()) {
				throw new ScrambleException("Event map line is missing a name: " + line);
			}
			mapping.put(original, scrambled);
			if ("1".equals(fields.get(2))) {
				nui.add(original);
			} else if (!"0".equals(fields.get(2))) {
				throw new ScrambleException("Event map nui flag must be 0 or 1: " + line);
			}
		}
		return new EventMap(mapping, nui);
	}

	static void write(Path path, Map<String, String> mapping, Set<String> nuiNames) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		List<String> originals = new ArrayList<String>(mapping.keySet());
		Collections.sort(originals);
		StringBuilder builder = new StringBuilder();
		builder.append(HEADER).append('\n');
		builder.append("# original, scrambled name, nui flag. Backslashes and tabs are escaped.\n");
		for (String original : originals) {
			builder.append(escape(original)).append('\t');
			builder.append(escape(mapping.get(original))).append('\t');
			builder.append(nuiNames.contains(original) ? '1' : '0').append('\n');
		}
		Files.write(path, builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	Map<String, String> scrambledToOriginal() {
		Map<String, String> reverse = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> entry : originalToScrambled.entrySet()) {
			reverse.put(entry.getValue(), entry.getKey());
		}
		return reverse;
	}

	Set<String> scrambledNuiNames() {
		Set<String> names = new LinkedHashSet<String>();
		for (String original : nuiOriginals) {
			String scrambled = originalToScrambled.get(original);
			if (scrambled != null) {
				names.add(scrambled);
			}
		}
		return names;
	}

	private static List<String> splitFields(String line) throws ScrambleException {
		List<String> fields = new ArrayList<String>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\\') {
				if (i + 1 >= line.length()) {
					throw new ScrambleException("Event map line has a trailing backslash: " + line);
				}
				char next = line.charAt(++i);
				switch (next) {
					case '\\':
						current.append('\\');
						break;
					case 't':
						current.append('\t');
						break;
					case 'n':
						current.append('\n');
						break;
					case 'r':
						current.append('\r');
						break;
					default:
						throw new ScrambleException("Event map line has an unknown escape: " + line);
				}
				continue;
			}
			if (c == '\t') {
				fields.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		fields.add(current.toString());
		return fields;
	}

	private static String escape(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\':
					builder.append("\\\\");
					break;
				case '\t':
					builder.append("\\t");
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
		return builder.toString();
	}
}
