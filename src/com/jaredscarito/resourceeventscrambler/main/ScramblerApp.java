package com.jaredscarito.resourceeventscrambler.main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

public class ScramblerApp extends Application {
	private final TextField resourcesField = new TextField();
	private final TextField configField = new TextField();
	private final TextField eventsField = new TextField();
	private final TextField mapField = new TextField();
	private final ListView<String> resourceList = new ListView<String>();
	private final ListView<String> eventList = new ListView<String>();
	private final ListView<String> fileList = new ListView<String>();
	private final TextArea log = new TextArea();
	private final Label status = new Label("Ready");
	private final Button runButton = new Button("Scramble");
	private final Button unscrambleButton = new Button("Unscramble");
	private final ProgressIndicator progress = new ProgressIndicator();
	private final List<javafx.scene.Node> formControls = new ArrayList<javafx.scene.Node>();
	private SplitPane split;
	private VBox headerBox;
	private boolean wideLayout;

	public static void launchApp(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		resourceList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		eventList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		fileList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		Path work = Paths.get(System.getProperty("user.dir"));
		Path resources = work.resolve("resources");
		resourcesField.setText(resources.toString());
		configField.setText(work.resolve("config").toString());
		eventsField.setText(work.resolve("RES_Anticheat").resolve("events.lua").toString());
		mapField.setText(ResourceScrambler.eventMapFile(resources).toString());
		loadConfigIntoLists(false);

		log.setEditable(false);
		log.setWrapText(true);
		log.setMinHeight(120);
		log.getStyleClass().add("log-view");
		log.setPromptText("Scrambler output appears here.");

		progress.setVisible(false);
		progress.setPrefSize(18, 18);
		runButton.getStyleClass().add("primary");
		unscrambleButton.getStyleClass().add("restore");
		runButton.setOnAction(event -> scramble(stage));
		unscrambleButton.setOnAction(event -> unscramble(stage));

		BorderPane root = new BorderPane();
		root.setTop(header());
		root.setCenter(body());
		root.setBottom(statusBar());

		Rectangle2D visual = Screen.getPrimary().getVisualBounds();
		double width = Math.min(visual.getWidth() - 32, Math.max(880, visual.getWidth() * 0.78));
		double height = Math.min(visual.getHeight() - 32, Math.max(620, visual.getHeight() * 0.82));
		Scene scene = new Scene(root, width, height);
		URL css = ScramblerApp.class.getResource("scrambler.css");
		if (css != null) {
			scene.getStylesheets().add(css.toExternalForm());
		}
		stage.setTitle("ResourceEventScrambler");
		stage.setMinWidth(Math.min(760, Math.max(480, visual.getWidth() - 24)));
		stage.setMinHeight(Math.min(520, Math.max(420, visual.getHeight() - 48)));
		stage.setX(visual.getMinX() + Math.max(0, (visual.getWidth() - width) / 2));
		stage.setY(visual.getMinY() + Math.max(0, (visual.getHeight() - height) / 2));
		stage.setScene(scene);
		scene.widthProperty().addListener((obs, previous, current) -> updateLayout(current.doubleValue()));
		scene.heightProperty().addListener((obs, previous, current) -> updateChrome(current.doubleValue()));
		updateLayout(width);
		updateChrome(height);
		stage.show();
	}

	private void updateLayout(double width) {
		if (split == null) {
			return;
		}
		boolean wide = width >= 1080;
		if (wide == wideLayout && split.getOrientation() == (wide ? Orientation.HORIZONTAL : Orientation.VERTICAL)) {
			return;
		}
		wideLayout = wide;
		split.setOrientation(wide ? Orientation.HORIZONTAL : Orientation.VERTICAL);
		split.setDividerPositions(wide ? 0.58 : 0.62);
	}

	private void updateChrome(double height) {
		if (headerBox == null) {
			return;
		}
		if (height < 760) {
			if (!headerBox.getStyleClass().contains("header-compact")) {
				headerBox.getStyleClass().add("header-compact");
			}
		} else {
			headerBox.getStyleClass().remove("header-compact");
		}
	}

	private VBox header() {
		Label title = new Label("ResourceEventScrambler");
		title.getStyleClass().add("header-title");
		Label subtitle = new Label("Scramble quoted FiveM event names, or restore them from the saved event map. A clean backup is kept beside the resources folder.");
		subtitle.getStyleClass().add("header-subtitle");
		subtitle.setWrapText(true);
		headerBox = new VBox(4, title, subtitle);
		headerBox.getStyleClass().add("header");
		return headerBox;
	}

	private SplitPane body() {
		VBox form = new VBox(12, pathCard(), blacklistCard());
		form.setFillWidth(true);
		form.setPadding(new Insets(16, 16, 8, 16));
		VBox.setVgrow(form.getChildren().get(1), Priority.ALWAYS);
		VBox logCard = logCard();
		logCard.setPadding(new Insets(8, 16, 16, 16));
		VBox.setVgrow(logCard.getChildren().get(1), Priority.ALWAYS);
		split = new SplitPane(form, logCard);
		split.setOrientation(Orientation.VERTICAL);
		split.setDividerPositions(0.62);
		return split;
	}

	private VBox pathCard() {
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		addPathRow(grid, 0, "Resources folder", resourcesField, "Folder", this::chooseResources);
		addPathRow(grid, 1, "Config file", configField, "File", this::chooseConfig);
		addPathRow(grid, 2, "Anticheat events", eventsField, "Save as", this::chooseEvents);
		addPathRow(grid, 3, "Event map", mapField, "Map", this::chooseMap);
		GridPane.setHgrow(resourcesField, Priority.ALWAYS);
		GridPane.setHgrow(configField, Priority.ALWAYS);
		GridPane.setHgrow(eventsField, Priority.ALWAYS);
		GridPane.setHgrow(mapField, Priority.ALWAYS);

		Button reload = new Button("Reload config");
		reload.setOnAction(event -> loadConfigIntoLists(true));
		Button save = new Button("Save config");
		save.setOnAction(event -> saveConfig(true));
		Button defaults = new Button("Restore defaults");
		defaults.setOnAction(event -> {
			fillLists(ScramblerConfig.defaultConfig());
			setStatus("Default blacklist loaded. Save config to write it to disk.");
		});
		formControls.add(reload);
		formControls.add(save);
		formControls.add(defaults);
		HBox actions = new HBox(8, reload, save, defaults);
		actions.setAlignment(Pos.CENTER_LEFT);

		Label title = new Label("Folders");
		title.getStyleClass().add("section-label");
		VBox card = new VBox(10, title, grid, actions);
		card.getStyleClass().add("card");
		return card;
	}

	private void addPathRow(GridPane grid, int row, String name, TextField field, String browseText, Runnable browse) {
		Label label = new Label(name);
		label.getStyleClass().add("field-label");
		label.setMinWidth(120);
		field.setPromptText(name);
		Button browseButton = new Button(browseText);
		browseButton.setMinWidth(84);
		browseButton.setOnAction(event -> browse.run());
		formControls.add(field);
		formControls.add(browseButton);
		grid.add(label, 0, row);
		grid.add(field, 1, row);
		grid.add(browseButton, 2, row);
	}

	private VBox blacklistCard() {
		TabPane tabs = new TabPane(
				listTab("Resources", resourceList, "Folder names that will not be modified. RES_Anticheat is always skipped.", false),
				listTab("Events", eventList, "Event names that stay unchanged, including playerConnecting and other server events.", false),
				listTab("Files", fileList, "Paths relative to the resources folder, such as jobs/client.lua.", true));
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		VBox.setVgrow(tabs, Priority.ALWAYS);
		Label title = new Label("Blacklist");
		title.getStyleClass().add("section-label");
		VBox card = new VBox(10, title, tabs);
		card.getStyleClass().add("card");
		VBox.setVgrow(card, Priority.ALWAYS);
		return card;
	}

	private Tab listTab(String name, ListView<String> list, String hintText, boolean browseInsideResources) {
		list.setPlaceholder(new Label("Nothing in this list."));
		list.setMinHeight(88);
		VBox.setVgrow(list, Priority.ALWAYS);
		TextField entry = new TextField();
		entry.setPromptText("Add a " + name.toLowerCase() + " entry");
		Button add = new Button("Add");
		add.setOnAction(event -> {
			if (addEntry(list, entry.getText())) {
				entry.clear();
			}
		});
		entry.setOnAction(event -> {
			if (addEntry(list, entry.getText())) {
				entry.clear();
			}
		});
		Button remove = new Button("Remove");
		remove.setOnAction(event -> removeSelected(list));
		HBox row = new HBox(8, entry, add, remove);
		HBox.setHgrow(entry, Priority.ALWAYS);
		row.setAlignment(Pos.CENTER_LEFT);
		formControls.add(list);
		formControls.add(entry);
		formControls.add(add);
		formControls.add(remove);
		if (browseInsideResources) {
			Button fromFolder = new Button("Add file...");
			fromFolder.setOnAction(event -> addFileFromResources(list));
			row.getChildren().add(fromFolder);
			formControls.add(fromFolder);
		}
		Label hint = new Label(hintText);
		hint.getStyleClass().add("hint");
		hint.setWrapText(true);
		VBox content = new VBox(8, hint, list, row);
		content.setPadding(new Insets(10, 4, 4, 4));
		VBox.setVgrow(list, Priority.ALWAYS);
		Tab tab = new Tab(name, content);
		return tab;
	}

	private VBox logCard() {
		Label title = new Label("Output");
		title.getStyleClass().add("section-label");
		Button clear = new Button("Clear");
		clear.setOnAction(event -> log.clear());
		HBox titleRow = new HBox(8, title, clear);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(title, Priority.ALWAYS);
		VBox card = new VBox(8, titleRow, log);
		VBox.setVgrow(log, Priority.ALWAYS);
		card.getStyleClass().add("card");
		return card;
	}

	private HBox statusBar() {
		status.getStyleClass().add("status-text");
		status.setWrapText(true);
		status.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(status, Priority.ALWAYS);
		HBox actions = new HBox(8, unscrambleButton, runButton);
		actions.setAlignment(Pos.CENTER_RIGHT);
		HBox bar = new HBox(12, progress, status, actions);
		bar.setAlignment(Pos.CENTER_LEFT);
		bar.getStyleClass().add("status-bar");
		return bar;
	}

	private void chooseResources() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Resources folder");
		Path current = currentPath(resourcesField);
		if (current != null && Files.isDirectory(current)) {
			chooser.setInitialDirectory(current.toFile());
		}
		Window owner = resourcesField.getScene() == null ? null : resourcesField.getScene().getWindow();
		java.io.File chosen = chooser.showDialog(owner);
		if (chosen != null) {
			resourcesField.setText(chosen.getAbsolutePath());
		}
	}

	private void chooseConfig() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Config file");
		Path current = currentPath(configField);
		if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
			chooser.setInitialDirectory(current.getParent().toFile());
			if (current.getFileName() != null) {
				chooser.setInitialFileName(current.getFileName().toString());
			}
		}
		Window owner = configField.getScene() == null ? null : configField.getScene().getWindow();
		java.io.File chosen = chooser.showOpenDialog(owner);
		if (chosen != null) {
			configField.setText(chosen.getAbsolutePath());
			loadConfigIntoLists(true);
		}
	}

	private void chooseMap() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Event map");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
		Path current = currentPath(mapField);
		if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
			chooser.setInitialDirectory(current.getParent().toFile());
			if (current.getFileName() != null) {
				chooser.setInitialFileName(current.getFileName().toString());
			}
		} else {
			chooser.setInitialFileName("event-map.txt");
		}
		Window owner = mapField.getScene() == null ? null : mapField.getScene().getWindow();
		java.io.File chosen = chooser.showSaveDialog(owner);
		if (chosen != null) {
			mapField.setText(chosen.getAbsolutePath());
		}
	}

	private void chooseEvents() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Anticheat events.lua");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lua files", "*.lua"));
		Path current = currentPath(eventsField);
		if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
			chooser.setInitialDirectory(current.getParent().toFile());
			if (current.getFileName() != null) {
				chooser.setInitialFileName(current.getFileName().toString());
			}
		} else {
			chooser.setInitialFileName("events.lua");
		}
		Window owner = eventsField.getScene() == null ? null : eventsField.getScene().getWindow();
		java.io.File chosen = chooser.showSaveDialog(owner);
		if (chosen != null) {
			eventsField.setText(chosen.getAbsolutePath());
		}
	}

	private void loadConfigIntoLists(boolean reportMissing) {
		Path config = currentPath(configField);
		if (config == null) {
			setStatus("Enter a config file path.");
			return;
		}
		try {
			if (Files.exists(config)) {
				fillLists(ScramblerConfig.load(config));
				setStatus("Loaded " + config.toAbsolutePath());
			} else if (reportMissing) {
				setStatus("No config at " + config.toAbsolutePath() + ". Save config to create it.");
			} else {
				fillLists(ScramblerConfig.defaultConfig());
				setStatus("Showing the default blacklist. Save config to write " + config.toAbsolutePath());
			}
		} catch (IOException e) {
			showError("Could not read the config", e.getMessage());
		}
	}

	private void fillLists(ScramblerConfig config) {
		replaceList(resourceList, config.blacklistedResources);
		replaceList(eventList, config.blacklistedEvents);
		replaceList(fileList, config.blacklistedFiles);
	}

	private void replaceList(ListView<String> list, Iterable<String> values) {
		List<String> sorted = new ArrayList<String>();
		for (String value : values) {
			sorted.add(value);
		}
		Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
		list.getItems().setAll(sorted);
	}

	private boolean addEntry(ListView<String> list, String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.isEmpty() || value.startsWith("#")) {
			return false;
		}
		if (list == fileList) {
			value = value.replace('\\', '/');
			while (value.startsWith("/")) {
				value = value.substring(1);
			}
		}
		for (String existing : list.getItems()) {
			if (existing.equalsIgnoreCase(value)) {
				setStatus("\"" + value + "\" is already in the list.");
				return false;
			}
		}
		list.getItems().add(value);
		Collections.sort(list.getItems(), String.CASE_INSENSITIVE_ORDER);
		setStatus("Added \"" + value + "\".");
		return true;
	}

	private void removeSelected(ListView<String> list) {
		List<String> selected = new ArrayList<String>(list.getSelectionModel().getSelectedItems());
		if (selected.isEmpty()) {
			setStatus("Select one or more entries to remove.");
			return;
		}
		list.getItems().removeAll(selected);
		setStatus("Removed " + selected.size() + " entr" + (selected.size() == 1 ? "y." : "ies."));
	}

	private void addFileFromResources(ListView<String> list) {
		Path resources = currentPath(resourcesField);
		if (resources == null || !Files.isDirectory(resources)) {
			showError("Resources folder", "Choose the resources folder before adding a file from it.");
			return;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("File inside resources");
		chooser.setInitialDirectory(resources.toFile());
		Window owner = list.getScene() == null ? null : list.getScene().getWindow();
		java.io.File chosen = chooser.showOpenDialog(owner);
		if (chosen == null) {
			return;
		}
		Path file = chosen.toPath().toAbsolutePath().normalize();
		Path root = resources.toAbsolutePath().normalize();
		if (!file.startsWith(root)) {
			showError("File is outside resources", "Choose a file inside " + root);
			return;
		}
		addEntry(list, root.relativize(file).toString());
	}

	private boolean saveConfig(boolean announce) {
		Path config = currentPath(configField);
		if (config == null) {
			showError("Config file", "Enter a path for the config file.");
			return false;
		}
		try {
			ScramblerConfig.save(config, resourceList.getItems(), eventList.getItems(), fileList.getItems());
			if (announce) {
				setStatus("Saved " + config.toAbsolutePath());
			}
			return true;
		} catch (IOException e) {
			showError("Could not save the config", e.getMessage());
			return false;
		}
	}

	private void scramble(Stage stage) {
		Path resources = currentPath(resourcesField);
		Path config = currentPath(configField);
		Path events = currentPath(eventsField);
		Path map = currentPath(mapField);
		if (resources == null || config == null || events == null || map == null) {
			showError("Missing path", "Choose the resources folder, config file, anticheat events file, and event map.");
			return;
		}
		if (!Files.isDirectory(resources)) {
			showError("Resources folder", "Could not find the resources folder at " + resources.toAbsolutePath());
			return;
		}
		Path backup = ResourceScrambler.backupDirectory(resources);
		if (Files.exists(backup)) {
			showError("Backup already exists", "A backup already exists at " + backup + ".\n\n"
					+ "Unscramble to restore the original names, or delete that backup before scrambling again.");
			return;
		}
		if (!confirm(stage, "Scramble resources", "Rewrite event names in " + resources.toAbsolutePath() + "?",
				"A clean copy will be saved to " + backup.getFileName() + ". The event map will be written to "
						+ map.toAbsolutePath() + ". Use Unscramble later to put the original names back.")) {
			return;
		}
		if (!saveConfig(false)) {
			return;
		}
		runJob(stage, "Scrambling resources...", "Scramble", () -> ResourceScrambler.run(resources, config, events, map),
				outcome -> outcome.mapping.size() + " event names were rewritten in " + outcome.filesChanged
						+ " files.\nBackup: " + backup + "\nEvent map: " + map.toAbsolutePath()
						+ "\nPut RES_Anticheat in the scrambled resources folder and start it from server.cfg.");
	}

	private void unscramble(Stage stage) {
		Path resources = currentPath(resourcesField);
		Path config = currentPath(configField);
		Path events = currentPath(eventsField);
		Path map = currentPath(mapField);
		if (resources == null || config == null || events == null || map == null) {
			showError("Missing path", "Choose the resources folder, config file, anticheat events file, and event map.");
			return;
		}
		if (!Files.isDirectory(resources)) {
			showError("Resources folder", "Could not find the resources folder at " + resources.toAbsolutePath());
			return;
		}
		if (!Files.isRegularFile(map)) {
			showError("Event map", "Could not find the event map at " + map.toAbsolutePath()
					+ ".\nScramble the resources first, and keep that file.");
			return;
		}
		if (!confirm(stage, "Unscramble resources", "Restore the original event names in " + resources.toAbsolutePath() + "?",
				"Names are restored from " + map.toAbsolutePath()
						+ ". The anticheat event list is cleared when files change. The clean backup folder is left in place.")) {
			return;
		}
		if (!saveConfig(false)) {
			return;
		}
		runJob(stage, "Restoring original event names...", "Unscramble",
				() -> ResourceScrambler.unscramble(resources, config, events, map),
				outcome -> "Restored " + outcome.mapping.size() + " names in " + outcome.filesChanged
						+ " files.\nThe clean backup is still beside the resources folder.");
	}

	private boolean confirm(Stage stage, String title, String header, String message) {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.initOwner(stage);
		confirm.setTitle(title);
		confirm.setHeaderText(header);
		confirm.setContentText(message);
		return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	private void runJob(Stage stage, String busyMessage, String action, Job job, ResultText resultText) {
		setBusy(true, busyMessage);
		log.appendText("[ResourceEventScrambler] UI started: " + action + "\n");
		LogWriter writer = new LogWriter();
		Task<ResourceScrambler.Outcome> task = new Task<ResourceScrambler.Outcome>() {
			@Override
			protected ResourceScrambler.Outcome call() throws Exception {
				PrintStream previousOut = System.out;
				PrintStream previousErr = System.err;
				PrintStream captured = new PrintStream(writer, true, "UTF-8");
				System.setOut(captured);
				System.setErr(captured);
				try {
					return job.run();
				} finally {
					captured.flush();
					writer.flushRemaining();
					System.setOut(previousOut);
					System.setErr(previousErr);
				}
			}
		};
		task.setOnSucceeded(event -> {
			ResourceScrambler.Outcome outcome = task.getValue();
			setBusy(false, null);
			setStatus(action + " finished. " + outcome.mapping.size() + " names, " + outcome.filesChanged + " files.");
			Alert done = new Alert(Alert.AlertType.INFORMATION);
			done.initOwner(stage);
			done.setTitle(action + " complete");
			done.setHeaderText(action + " complete");
			done.setContentText(resultText.describe(outcome));
			done.show();
		});
		task.setOnFailed(event -> {
			setBusy(false, null);
			Throwable error = task.getException();
			String message = error.getMessage() == null ? error.toString() : error.getMessage();
			log.appendText("[ResourceEventScrambler] " + message + "\n");
			setStatus(action + " failed.");
			showError(action + " failed", message);
		});
		Thread worker = new Thread(task, "resource-event-scrambler");
		worker.setDaemon(true);
		worker.start();
	}

	private void setBusy(boolean busy, String busyMessage) {
		runButton.setDisable(busy);
		unscrambleButton.setDisable(busy);
		progress.setVisible(busy);
		for (javafx.scene.Node control : formControls) {
			control.setDisable(busy);
		}
		if (busy && busyMessage != null) {
			setStatus(busyMessage);
		}
	}

	private interface Job {
		ResourceScrambler.Outcome run() throws Exception;
	}

	private interface ResultText {
		String describe(ResourceScrambler.Outcome outcome);
	}

	private void setStatus(String message) {
		status.setText(message);
	}

	private Path currentPath(TextField field) {
		String text = field.getText();
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		return Paths.get(text.trim());
	}

	private void showError(String header, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		if (status.getScene() != null) {
			alert.initOwner(status.getScene().getWindow());
		}
		alert.setTitle("ResourceEventScrambler");
		alert.setHeaderText(header);
		alert.setContentText(message == null ? "" : message);
		alert.showAndWait();
	}

	private final class LogWriter extends OutputStream {
		private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
		private boolean scheduled;

		@Override
		public synchronized void write(int value) {
			pending.write(value);
			if (value == '\n') {
				schedule();
			}
		}

		@Override
		public synchronized void write(byte[] bytes, int offset, int length) {
			pending.write(bytes, offset, length);
			for (int i = offset; i < offset + length; i++) {
				if (bytes[i] == '\n') {
					schedule();
					return;
				}
			}
		}

		synchronized void flushRemaining() {
			if (pending.size() > 0) {
				schedule();
			}
		}

		private void schedule() {
			if (scheduled) {
				return;
			}
			scheduled = true;
			Platform.runLater(() -> {
				String text;
				synchronized (LogWriter.this) {
					text = pending.toString(StandardCharsets.UTF_8);
					pending.reset();
					scheduled = false;
				}
				if (!text.isEmpty()) {
					log.appendText(text);
					log.positionCaret(log.getLength());
				}
			});
		}
	}
}
