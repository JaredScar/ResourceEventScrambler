package com.jaredscarito.resourceeventscrambler.main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One line per scramble or unscramble, stored beside the project root. */
final class RunHistory {
	final long when;
	final String action;
	final boolean success;
	final int names;
	final int files;
	final String detail;

	private RunHistory(long when, String action, boolean success, int names, int files, String detail) {
		this.when = when;
		this.action = action;
		this.success = success;
		this.names = names;
		this.files = files;
		this.detail = detail;
	}

	static Path file(Path projectRoot) {
		return projectRoot.resolve("scramble-history.txt");
	}

	static void append(Path projectRoot, String action, boolean success, int names, int files, String detail) {
		String safe = detail == null ? "" : detail.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
		String line = System.currentTimeMillis() + "\t" + action + "\t" + (success ? "ok" : "fail") + "\t" + names + "\t" + files + "\t" + safe + "\n";
		try {
			Files.write(file(projectRoot), line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// History is a convenience. A failed write should not fail the scramble.
		}
	}

	static List<RunHistory> read(Path projectRoot) {
		Path path = file(projectRoot);
		if (!Files.isRegularFile(path)) {
			return Collections.emptyList();
		}
		List<RunHistory> runs = new ArrayList<RunHistory>();
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String[] parts = line.split("\t", 6);
				if (parts.length < 6) {
					continue;
				}
				runs.add(new RunHistory(Long.parseLong(parts[0]), parts[1], "ok".equals(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5]));
			}
		} catch (IOException | NumberFormatException ignored) {
			return runs;
		}
		Collections.reverse(runs);
		return runs;
	}

	static void clear(Path projectRoot) throws IOException {
		Files.deleteIfExists(file(projectRoot));
	}

	String whenLabel() {
		LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(when), ZoneId.systemDefault());
		String clock = time.format(DateTimeFormatter.ofPattern("h:mm a"));
		LocalDate day = time.toLocalDate();
		LocalDate today = LocalDate.now();
		if (day.equals(today)) {
			return "Today, " + clock;
		}
		if (day.equals(today.minusDays(1))) {
			return "Yesterday, " + clock;
		}
		return time.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
	}
}
