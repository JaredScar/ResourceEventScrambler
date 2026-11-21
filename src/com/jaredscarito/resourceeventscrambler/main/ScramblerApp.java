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
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class ScramblerApp extends Application {
	private static final String BUILTIN_RESOURCE = "RES_Anticheat";

	private final TextField resourcesField = new TextField();
	private final TextField configField = new TextField();
	private final TextField eventsField = new TextField();
	private final TextField mapField = new TextField();
	private final ListView<String> resourceList = new ListView<String>();
	private final ListView<String> eventList = new ListView<String>();
	private final ListView<String> fileList = new ListView<String>();
	private final TextArea log = new TextArea();
	private final List<Node> formControls = new ArrayList<Node>();

	private Stage stage;
	private Path projectRoot;
	private boolean suppressDirty;
	private String savedSnapshot = "";
	private int exclusionKind;
	private int chipKind;

	private Label projectName;
	private Label projectPath;
	private Label navCount;
	private VBox unsavedBox;
	private Label unsavedDetail;
	private Label activity;
	private Label heroTitle;
	private Label heroPath;
	private Label heroBody;
	private Label heroBadge;
	private Label resourcesRel;
	private Label configRel;
	private Label eventsRel;
	private Label mapRel;
	private Label resourcesState;
	private Label configState;
	private Label eventsState;
	private Label mapState;
	private Label backupRel;
	private Label backupState;
	private Label overviewResourcesRel;
	private Label overviewConfigRel;
	private Label overviewEventsRel;
	private Label overviewMapRel;
	private Label overviewResourcesState;
	private Label overviewConfigState;
	private Label overviewEventsState;
	private Label overviewMapState;
	private FlowPane chips;
	private Label chipHint;
	private HBox chipSegments;
	private VBox exclusionRows;
	private TextField exclusionFilter;
	private Label exclusionFooter;
	private Label statResourceCount;
	private Label statEventCount;
	private Label statFileCount;
	private VBox statResourceCard;
	private VBox statEventCard;
	private VBox statFileCard;
	private VBox mapRows;
	private Label mapNames;
	private Label mapEvents;
	private Label mapNui;
	private Label mapFileLabel;
	private TextField mapSearch;
	private HBox mapFilters;
	private int mapFilter;
	private VBox historyRows;
	private Label historySummary;
	private HBox historyFilters;
	private int historyFilter;
	private StackPane pages;
	private final List<Button> navButtons = new ArrayList<Button>();

	public static void launchApp(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primary) {
		stage = primary;
		projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
		resourcesField.setText("./resources");
		configField.setText("./config");
		eventsField.setText("./RES_Anticheat/events.lua");
		mapField.setText("./event-map.txt");

		log.setEditable(false);
		log.setWrapText(true);
		log.getStyleClass().add("log-view");
		log.setPromptText(">  waiting for a run. Press Scramble or Ctrl L");
		log.setMinHeight(120);

		ListChangeListener<String> onList = change -> {
			if (!suppressDirty) {
				refreshDynamic();
			}
		};
		resourceList.getItems().addListener(onList);
		eventList.getItems().addListener(onList);
		fileList.getItems().addListener(onList);

		BorderPane root = new BorderPane();
		root.getStyleClass().add("app-root");
		root.setLeft(sidebar());
		pages = new StackPane(overviewPage(), exclusionsPage(), pathsPage(), mapPage(), historyPage());
		root.setCenter(pages);

		Rectangle2D visual = Screen.getPrimary().getVisualBounds();
		double width = Math.min(visual.getWidth() - 48, Math.max(1180, visual.getWidth() * 0.86));
		double height = Math.min(visual.getHeight() - 48, Math.max(760, visual.getHeight() * 0.88));
		Scene scene = new Scene(root, width, height, Color.web("#0c0c0c"));
		URL css = ScramblerApp.class.getResource("scrambler.css");
		if (css != null) {
			scene.getStylesheets().add(css.toExternalForm());
		}
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), () -> saveConfig(true));
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> scramble(stage));
		stage.setTitle("ResourceEventScrambler");
		stage.setMinWidth(980);
		stage.setMinHeight(640);
		stage.setX(visual.getMinX() + Math.max(0, (visual.getWidth() - width) / 2));
		stage.setY(visual.getMinY() + Math.max(0, (visual.getHeight() - height) / 2));
		stage.setScene(scene);
		loadConfigIntoLists(false);
		showPage("overview");
		stage.show();
	}

	private VBox sidebar() {
		Label mark = new Label("\u2715");
		mark.getStyleClass().add("logo-mark");
		Label name = new Label("Scrambler");
		name.getStyleClass().add("brand-name");
		Label sub = new Label("FiveM event protection");
		sub.getStyleClass().add("brand-sub");
		VBox brandText = new VBox(0, name, sub);
		HBox brand = new HBox(10, mark, brandText);
		brand.setAlignment(Pos.CENTER_LEFT);
		brand.getStyleClass().add("brand");

		projectName = new Label(projectRoot.getFileName() == null ? "Project" : projectRoot.getFileName().toString());
		projectName.getStyleClass().add("project-name");
		projectPath = new Label(projectRoot.toString());
		projectPath.getStyleClass().add("project-path");
		projectPath.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
		projectPath.setMaxWidth(180);
		Button change = new Button("\u21bb");
		change.getStyleClass().add("icon-button");
		change.setOnAction(event -> changeRoot());
		VBox projectText = new VBox(1, projectName, projectPath);
		HBox.setHgrow(projectText, Priority.ALWAYS);
		HBox project = new HBox(8, projectText, change);
		project.setAlignment(Pos.CENTER_LEFT);
		project.getStyleClass().add("project-card");

		VBox nav = new VBox(4,
				navButton("overview", "Overview", "\uE80F"),
				navButton("exclusions", "Exclusions", "\uEA3A"),
				navButton("paths", "Paths", "\uE8B7"),
				navButton("map", "Event map", "\uE8FD"),
				navButton("history", "History", "\uE81C"));
		nav.getStyleClass().add("nav");

		Label unsavedTitle = new Label("Unsaved changes");
		unsavedTitle.getStyleClass().add("unsaved-title");
		unsavedDetail = new Label("Exclusion changes are not written yet.");
		unsavedDetail.getStyleClass().add("unsaved-detail");
		unsavedDetail.setWrapText(true);
		Button save = new Button("Save config");
		save.getStyleClass().add("save-dark");
		save.setMaxWidth(Double.MAX_VALUE);
		save.setOnAction(event -> saveConfig(true));
		HBox saveRow = new HBox(8, save, shortcut("Ctrl S"));
		saveRow.setAlignment(Pos.CENTER_LEFT);
		unsavedBox = new VBox(8, unsavedTitle, unsavedDetail, saveRow);
		unsavedBox.getStyleClass().add("unsaved");
		unsavedBox.setVisible(false);
		unsavedBox.setManaged(false);

		Region grow = new Region();
		VBox.setVgrow(grow, Priority.ALWAYS);
		VBox side = new VBox(16, brand, project, nav, grow, unsavedBox);
		side.getStyleClass().add("sidebar");
		side.setPrefWidth(268);
		side.setMinWidth(248);
		return side;
	}

	private Button navButton(String id, String title, String glyph) {
		Label icon = new Label(glyph);
		icon.getStyleClass().add("nav-icon");
		Label text = new Label(title);
		text.getStyleClass().add("nav-text");
		HBox row = new HBox(10, icon, text);
		row.setAlignment(Pos.CENTER_LEFT);
		if ("exclusions".equals(id)) {
			navCount = new Label("0");
			navCount.getStyleClass().add("nav-count");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			row.getChildren().addAll(spacer, navCount);
		}
		Button button = new Button();
		button.setGraphic(row);
		button.setMaxWidth(Double.MAX_VALUE);
		button.setAlignment(Pos.CENTER_LEFT);
		button.getStyleClass().add("nav-button");
		button.setUserData(id);
		button.setOnAction(event -> showPage(id));
		row.maxWidthProperty().bind(button.widthProperty().subtract(24));
		row.minWidthProperty().bind(button.widthProperty().subtract(24));
		navButtons.add(button);
		return button;
	}

	private Node overviewPage() {
		Label title = new Label("Overview");
		title.getStyleClass().add("page-title");
		Button unscramble = new Button("\u21ba  Unscramble");
		unscramble.getStyleClass().add("ghost");
		unscramble.setOnAction(event -> unscramble(stage));
		formControls.add(unscramble);
		HBox heading = heading(title, "Rename quoted events so injected menus miss, then catch anyone still firing the old names.", unscramble);

		heroBadge = new Label("Not scrambled");
		heroBadge.getStyleClass().add("badge");
		Button scramble = actionButton("\u21c4   Scramble resources", "Ctrl L", "scramble");
		scramble.setOnAction(event -> scramble(stage));
		formControls.add(scramble);
		Label backupNote = new Label("A clean backup is made first.");
		backupNote.getStyleClass().add("tiny");
		VBox scrambleCol = new VBox(4, scramble, backupNote);
		scrambleCol.setAlignment(Pos.CENTER_RIGHT);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox heroTop = new HBox(12, heroBadge, spacer, scrambleCol);
		heroTop.setAlignment(Pos.CENTER_LEFT);
		heroTitle = new Label("Ready to scramble");
		heroTitle.getStyleClass().add("hero-title");
		heroPath = new Label("./resources");
		heroPath.getStyleClass().add("hero-path");
		HBox heroLine = new HBox(10, heroTitle, heroPath);
		heroLine.setAlignment(Pos.BASELINE_LEFT);
		heroBody = new Label("Every quoted event name gets a random replacement. RES_Anticheat receives the old names so it can flag anyone who still fires them.");
		heroBody.getStyleClass().add("muted");
		heroBody.setWrapText(true);
		HBox steps = new HBox(10,
				step("01", "Back up", "Clean copy saved beside the resources folder."),
				step("02", "Rewrite", "Quoted event names replaced in every Lua and JS file."),
				step("03", "Map", "Old and new pairs written to event-map.txt."),
				step("04", "Arm", "Old names handed to events.lua to watch."));
		for (Node step : steps.getChildren()) {
			HBox.setHgrow(step, Priority.ALWAYS);
		}
		VBox hero = new VBox(14, heroTop, heroLine, heroBody, steps);
		hero.getStyleClass().add("card");

		chips = new FlowPane(8, 8);
		chipHint = new Label("Whole resource folders that are never touched.");
		chipHint.getStyleClass().add("tiny");
		chipHint.setWrapText(true);
		chipSegments = new HBox(6,
				chipSegment("Resources", 0),
				chipSegment("Events", 1),
				chipSegment("Files", 2));
		Button manage = link("Manage all  \u2192");
		manage.setOnAction(event -> {
			exclusionKind = chipKind;
			showPage("exclusions");
		});
		Label exclusionsTitle = new Label("Exclusions");
		exclusionsTitle.getStyleClass().add("section-label");
		VBox exclusions = new VBox(10, headerLine(exclusionsTitle, manage), chipHint, chipSegments, chips);
		exclusions.getStyleClass().add("card");
		HBox.setHgrow(exclusions, Priority.ALWAYS);

		overviewResourcesRel = new Label();
		overviewConfigRel = new Label();
		overviewEventsRel = new Label();
		overviewMapRel = new Label();
		overviewResourcesState = new Label();
		overviewConfigState = new Label();
		overviewEventsState = new Label();
		overviewMapState = new Label();
		Button editPaths = link("Edit paths  \u2192");
		editPaths.setOnAction(event -> showPage("paths"));
		VBox paths = new VBox(10,
				headerLine(new Label("Paths"), editPaths),
				overviewPath("Resources", overviewResourcesRel, overviewResourcesState),
				overviewPath("Config", overviewConfigRel, overviewConfigState),
				overviewPath("Anticheat events", overviewEventsRel, overviewEventsState),
				overviewPath("Event map", overviewMapRel, overviewMapState));
		((Label) ((HBox) paths.getChildren().get(0)).getChildren().get(0)).getStyleClass().add("section-label");
		paths.getStyleClass().add("card");
		paths.setPrefWidth(280);
		HBox middle = new HBox(12, exclusions, paths);
		HBox.setHgrow(exclusions, Priority.ALWAYS);

		activity = new Label("Idle");
		activity.getStyleClass().add("activity");
		Button past = link("Past runs");
		past.setOnAction(event -> showPage("history"));
		Button copy = link("Copy");
		copy.setOnAction(event -> copyText(log.getText()));
		Button clear = link("Clear");
		clear.setOnAction(event -> log.clear());
		Label outputTitle = new Label("Output");
		outputTitle.getStyleClass().add("section-label");
		HBox outputHead = new HBox(10, outputTitle, activity, past, new Region(), copy, clear);
		HBox.setHgrow(outputHead.getChildren().get(3), Priority.ALWAYS);
		outputHead.setAlignment(Pos.CENTER_LEFT);
		outputHead.getStyleClass().add("console-chrome");
		VBox output = new VBox(outputHead, log);
		output.getStyleClass().addAll("card", "console-card");
		output.setMaxHeight(220);

		VBox upper = new VBox(16, heading, hero, middle);
		upper.setFillWidth(true);
		upper.setPadding(new Insets(28, 32, 12, 32));
		ScrollPane scroll = new ScrollPane(upper);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("page");
		BorderPane frame = new BorderPane();
		frame.setId("overview");
		frame.getStyleClass().add("page-frame");
		frame.setCenter(scroll);
		frame.setBottom(output);
		BorderPane.setMargin(output, new Insets(0, 32, 22, 32));
		return frame;
	}

	private ScrollPane exclusionsPage() {
		Label title = new Label("Exclusions");
		title.getStyleClass().add("page-title");
		Button defaults = new Button("Restore defaults");
		defaults.getStyleClass().add("ghost");
		defaults.setOnAction(event -> {
			fillLists(ScramblerConfig.defaultConfig());
			markDirty("Default lists are loaded. Save config to write them.");
		});
		Button save = actionButton("Save config", "Ctrl S", "scramble");
		save.setOnAction(event -> saveConfig(true));
		HBox actions = new HBox(8, defaults, save);
		HBox heading = heading(title, "Anything listed here keeps its original name. Everything else in your resources gets scrambled.", actions);

		statResourceCount = new Label("0");
		statEventCount = new Label("0");
		statFileCount = new Label("0");
		statResourceCard = statCard(statResourceCount, "Resources", "Whole folders that are never touched", 0);
		statEventCard = statCard(statEventCount, "Events", "Names that are never renamed", 1);
		statFileCard = statCard(statFileCount, "Files", "Single files, relative to resources", 2);
		HBox stats = new HBox(12, statResourceCard, statEventCard, statFileCard);
		for (Node card : stats.getChildren()) {
			HBox.setHgrow(card, Priority.ALWAYS);
		}

		exclusionFilter = new TextField();
		exclusionFilter.setPromptText("Filter, or type a name and press Enter");
		exclusionFilter.getStyleClass().add("filter-field");
		exclusionFilter.setOnAction(event -> addFromFilter());
		exclusionFilter.textProperty().addListener((obs, previous, current) -> refreshExclusions());
		HBox.setHgrow(exclusionFilter, Priority.ALWAYS);
		Button add = new Button("+  Add");
		add.getStyleClass().add("ghost");
		add.setOnAction(event -> {
			if (exclusionKind == 2 && (exclusionFilter.getText() == null || exclusionFilter.getText().trim().isEmpty())) {
				addFileFromResources(fileList);
			} else {
				addFromFilter();
			}
		});
		formControls.add(exclusionFilter);
		formControls.add(add);
		HBox filterRow = new HBox(8, exclusionFilter, add);
		filterRow.setAlignment(Pos.CENTER_LEFT);

		Label nameHead = new Label("NAME");
		Label sourceHead = new Label("SOURCE");
		nameHead.getStyleClass().add("column-head");
		sourceHead.getStyleClass().add("column-head");
		Region headSpace = new Region();
		HBox.setHgrow(headSpace, Priority.ALWAYS);
		HBox head = new HBox(12, nameHead, headSpace, sourceHead);
		head.getStyleClass().add("table-head");
		exclusionRows = new VBox();
		exclusionRows.getStyleClass().add("table");
		ScrollPane rowScroll = new ScrollPane(exclusionRows);
		rowScroll.setFitToWidth(true);
		rowScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		rowScroll.getStyleClass().add("table-scroll");
		rowScroll.setMinHeight(280);
		rowScroll.setPrefViewportHeight(460);
		VBox.setVgrow(rowScroll, Priority.ALWAYS);
		exclusionFooter = new Label();
		exclusionFooter.getStyleClass().add("tiny");
		Label enterHint = new Label("Press Enter to add what you typed");
		enterHint.getStyleClass().add("tiny");
		Region footerSpace = new Region();
		HBox.setHgrow(footerSpace, Priority.ALWAYS);
		HBox footer = new HBox(8, exclusionFooter, footerSpace, enterHint);
		footer.setAlignment(Pos.CENTER_LEFT);

		VBox page = new VBox(16, heading, stats, filterRow, head, rowScroll, footer);
		return page("exclusions", page);
	}

	private ScrollPane pathsPage() {
		Label title = new Label("Paths");
		title.getStyleClass().add("page-title");
		Button defaults = new Button("Restore defaults");
		defaults.getStyleClass().add("ghost");
		defaults.setOnAction(event -> resetPathsToRoot());
		HBox heading = heading(title, "Where the scrambler reads and writes. Paths are shown relative to the project root, and all of them reset when you change that root.", defaults);

		Label rootValue = new Label();
		rootValue.getStyleClass().add("path-absolute");
		rootValue.textProperty().bind(projectPath.textProperty());
		Button change = new Button("Change root...");
		change.getStyleClass().add("ghost");
		change.setOnAction(event -> changeRoot());
		VBox rootText = new VBox(2, eyebrow("PROJECT ROOT"), rootValue);
		HBox.setHgrow(rootText, Priority.ALWAYS);
		HBox rootCard = new HBox(12, rootText, change);
		rootCard.setAlignment(Pos.CENTER_LEFT);
		rootCard.getStyleClass().add("card");

		resourcesRel = new Label();
		configRel = new Label();
		eventsRel = new Label();
		mapRel = new Label();
		resourcesState = new Label();
		configState = new Label();
		eventsState = new Label();
		mapState = new Label();
		backupRel = new Label();
		backupState = new Label();

		VBox resourcesCard = pathCard("Resources folder", "The FiveM resources you want scrambled. Everything inside is rewritten except your exclusions.", resourcesField, resourcesRel, resourcesState, "Browse", this::chooseResources);
		VBox configCard = pathCard("Config file", "Holds your exclusion lists. It is saved before every run.", configField, configRel, configState, "Browse", this::chooseConfig);
		VBox eventsCard = pathCard("Anticheat events", "RES_Anticheat reads the old event names from this Lua file and flags anyone who fires them.", eventsField, eventsRel, eventsState, "Save as", this::chooseEvents);
		VBox mapCard = pathCard("Event map", "Every old to new name pair. Unscramble needs it to put names back.", mapField, mapRel, mapState, "Save as", this::chooseMap);
		Label warn = new Label("Keep this file safe. Without it, scrambled names cannot be restored from the map.");
		warn.getStyleClass().add("warn-line");
		warn.setWrapText(true);
		mapCard.getChildren().add(warn);

		HBox row1 = new HBox(12, resourcesCard, configCard);
		HBox row2 = new HBox(12, eventsCard, mapCard);
		HBox.setHgrow(resourcesCard, Priority.ALWAYS);
		HBox.setHgrow(configCard, Priority.ALWAYS);
		HBox.setHgrow(eventsCard, Priority.ALWAYS);
		HBox.setHgrow(mapCard, Priority.ALWAYS);

		VBox backupText = new VBox(4,
				headerLine(new Label("Clean backup"), backupState),
				new Label("An untouched copy of your resources, made before anything is rewritten. It is placed beside the resources folder."));
		((Label) backupText.getChildren().get(1)).getStyleClass().add("muted");
		((Label) backupText.getChildren().get(1)).setWrapText(true);
		((Label) ((HBox) backupText.getChildren().get(0)).getChildren().get(0)).getStyleClass().add("section-label");
		HBox.setHgrow(backupText, Priority.ALWAYS);
		backupRel.getStyleClass().add("path-value");
		HBox backup = new HBox(12, backupText, backupRel);
		backup.setAlignment(Pos.CENTER_LEFT);
		backup.getStyleClass().add("card");

		VBox page = new VBox(14, heading, rootCard, row1, row2, backup);
		return page("paths", page);
	}

	private ScrollPane mapPage() {
		Label title = new Label("Event map");
		title.getStyleClass().add("page-title");
		Button open = new Button("Open file");
		open.getStyleClass().add("ghost");
		open.setOnAction(event -> openPath(currentPath(mapField)));
		Button copy = new Button("Copy all");
		copy.getStyleClass().add("ghost");
		copy.setOnAction(event -> copyMap());
		HBox heading = heading(title, "Every renamed event, old to new. Unscramble reads this file to put names back, and RES_Anticheat watches the old names.", new HBox(8, open, copy));

		mapNames = new Label("0");
		mapEvents = new Label("0");
		mapNui = new Label("0");
		mapFileLabel = new Label("./event-map.txt");
		HBox stats = new HBox(12,
				metric(mapNames, "Names renamed"),
				metric(mapEvents, "Server and client events"),
				metric(mapNui, "NUI callbacks"),
				metric(mapFileLabel, "File"));
		for (Node card : stats.getChildren()) {
			HBox.setHgrow(card, Priority.ALWAYS);
		}
		mapFileLabel.getStyleClass().remove("stat-count");
		mapFileLabel.getStyleClass().add("file-metric");

		mapSearch = new TextField();
		mapSearch.setPromptText("Search old or new names");
		mapSearch.getStyleClass().add("filter-field");
		mapSearch.textProperty().addListener((obs, previous, current) -> refreshMap());
		HBox.setHgrow(mapSearch, Priority.ALWAYS);
		mapFilters = new HBox(6, filterButton("All", 0), filterButton("Events", 1), filterButton("NUI", 2));
		HBox searchRow = new HBox(10, mapSearch, mapFilters);
		searchRow.setAlignment(Pos.CENTER_LEFT);

		mapRows = new VBox();
		mapRows.getStyleClass().add("table");
		VBox page = new VBox(14, heading, stats, searchRow, mapHead(), mapRows);
		return page("map", page);
	}

	private ScrollPane historyPage() {
		Label title = new Label("History");
		title.getStyleClass().add("page-title");
		Button clear = new Button("Clear history");
		clear.getStyleClass().add("ghost");
		clear.setOnAction(event -> {
			try {
				RunHistory.clear(projectRoot);
				refreshHistory();
			} catch (IOException e) {
				showError("Could not clear history", e.getMessage());
			}
		});
		HBox heading = heading(title, "Every scramble and unscramble run from this window, with what changed.", clear);
		historySummary = new Label();
		historySummary.getStyleClass().add("tiny");
		historyFilters = new HBox(6, historyButton("All", 0), historyButton("Scrambles", 1), historyButton("Unscrambles", 2), historyButton("Failed", 3));
		historyRows = new VBox(10);
		VBox page = new VBox(14, heading, historySummary, historyFilters, historyRows);
		return page("history", page);
	}

	private void showPage(String id) {
		for (Node page : pages.getChildren()) {
			boolean on = id.equals(page.getId());
			page.setVisible(on);
			page.setManaged(on);
		}
		for (Button button : navButtons) {
			if (id.equals(button.getUserData())) {
				if (!button.getStyleClass().contains("selected")) {
					button.getStyleClass().add("selected");
				}
			} else {
				button.getStyleClass().remove("selected");
			}
		}
		refreshDynamic();
	}

	private void refreshDynamic() {
		int total = resourceList.getItems().size() + eventList.getItems().size() + fileList.getItems().size();
		if (navCount != null) {
			navCount.setText(Integer.toString(total));
		}
		boolean dirty = !snapshot().equals(savedSnapshot);
		unsavedBox.setVisible(dirty);
		unsavedBox.setManaged(dirty);
		Path config = currentPath(configField);
		if (config == null || !Files.isRegularFile(config)) {
			unsavedDetail.setText("The default lists are not written to the config file yet.");
		} else {
			unsavedDetail.setText("Exclusion changes are not written to the config file yet.");
		}
		refreshHero();
		refreshPathLabels();
		refreshChips();
		refreshExclusions();
		refreshMap();
		refreshHistory();
		if (projectName != null && projectRoot.getFileName() != null) {
			projectName.setText(projectRoot.getFileName().toString());
			projectPath.setText(projectRoot.toString());
		}
	}

	private void refreshHero() {
		Path resources = currentPath(resourcesField);
		String shown = resources == null ? "./resources" : displayPath(resources);
		Path backup = resources == null ? null : ResourceScrambler.backupDirectory(resources);
		boolean scrambled = backup != null && Files.exists(backup);
		heroBadge.setText(scrambled ? "Scrambled" : "Not scrambled");
		heroBadge.getStyleClass().removeAll("badge-on", "badge");
		heroBadge.getStyleClass().add(scrambled ? "badge-on" : "badge");
		boolean missing = resources == null || !Files.isDirectory(resources);
		heroPath.setVisible(!scrambled);
		heroPath.setManaged(!scrambled);
		heroPath.setText(shown);
		if (missing) {
			heroTitle.setText("Ready to scramble");
			heroBody.setText("That folder is not on disk yet. Choose the live resources folder on the Paths page before scrambling.");
		} else if (scrambled) {
			heroTitle.setText("Names in " + shown + " are scrambled");
			heroBody.setText("Unscramble puts the original names back from the event map. The clean backup stays beside the resources folder.");
		} else {
			heroTitle.setText("Ready to scramble");
			heroBody.setText("Every quoted event name gets a random replacement, except the " + (resourceList.getItems().size() + eventList.getItems().size() + fileList.getItems().size()) + " entries you have excluded. RES_Anticheat receives the old names so it can flag anyone who still fires them.");
		}
	}

	private void refreshPathLabels() {
		setPath(resourcesField, resourcesRel, resourcesState, overviewResourcesRel, overviewResourcesState, true, "Missing");
		setPath(configField, configRel, configState, overviewConfigRel, overviewConfigState, false, "Created on save");
		setPath(eventsField, eventsRel, eventsState, overviewEventsRel, overviewEventsState, false, "Missing");
		setPath(mapField, mapRel, mapState, overviewMapRel, overviewMapState, false, "Created on run");
		Path resources = currentPath(resourcesField);
		Path backup = resources == null ? null : ResourceScrambler.backupDirectory(resources);
		backupRel.setText(backup == null ? "resources [UNSCRAMBLED]" : displayPath(backup));
		boolean exists = backup != null && Files.exists(backup);
		backupState.setText(exists ? "Found" : "Created on first scramble");
		styleState(backupState, exists);
	}

	private void setPath(TextField field, Label rel, Label state, Label overviewRel, Label overviewState, boolean directory, String missing) {
		Path path = currentPath(field);
		String shown = path == null ? "" : displayPath(path);
		rel.setText(path == null ? "" : path.toAbsolutePath().normalize().toString());
		overviewRel.setText(shown);
		boolean found = path != null && (directory ? Files.isDirectory(path) : Files.isRegularFile(path));
		String label = found ? "Found" : missing;
		state.setText(label);
		overviewState.setText(label);
		styleState(state, found);
		styleState(overviewState, found);
	}

	private void refreshChips() {
		chips.getChildren().clear();
		ListView<String> list = listFor(chipKind);
		List<String> ordered = displayOrder(list);
		chipHint.setText(chipKind == 0 ? "Whole resource folders that are never touched." : chipKind == 1 ? "Event names that stay exactly as written." : "Single files, relative to the resources folder.");
		int shown = 0;
		for (String item : ordered) {
			if (shown == 40) {
				Label more = new Label("+" + (ordered.size() - shown));
				more.getStyleClass().add("chip-more");
				chips.getChildren().add(more);
				break;
			}
			chips.getChildren().add(chip(list, item));
			shown++;
		}
		if (chipSegments.getChildren().size() == 3) {
			((Button) chipSegments.getChildren().get(0)).setText("Resources   " + resourceList.getItems().size());
			((Button) chipSegments.getChildren().get(1)).setText("Events   " + eventList.getItems().size());
			((Button) chipSegments.getChildren().get(2)).setText("Files   " + fileList.getItems().size());
		}
		markSegments(chipSegments, chipKind);
	}

	private void refreshExclusions() {
		statResourceCount.setText(Integer.toString(resourceList.getItems().size()));
		statEventCount.setText(Integer.toString(eventList.getItems().size()));
		statFileCount.setText(Integer.toString(fileList.getItems().size()));
		selectStat(statResourceCard, exclusionKind == 0);
		selectStat(statEventCard, exclusionKind == 1);
		selectStat(statFileCard, exclusionKind == 2);
		exclusionFilter.setPromptText(exclusionKind == 0 ? "Filter, or type a folder name to add" : exclusionKind == 1 ? "Filter, or type an event name to add" : "Filter, or type a path such as jobs/client.lua");
		String query = exclusionFilter.getText() == null ? "" : exclusionFilter.getText().trim().toLowerCase();
		ListView<String> list = listFor(exclusionKind);
		exclusionRows.getChildren().clear();
		int shown = 0;
		for (String item : displayOrder(list)) {
			if (!query.isEmpty() && item.toLowerCase().indexOf(query) < 0) {
				continue;
			}
			exclusionRows.getChildren().add(exclusionRow(list, item));
			shown++;
		}
		String noun = exclusionKind == 0 ? "resources" : exclusionKind == 1 ? "events" : "files";
		exclusionFooter.setText(shown + " " + noun + (query.isEmpty() ? "" : " matching"));
	}

	private void refreshMap() {
		if (mapRows == null) {
			return;
		}
		markSegments(mapFilters, mapFilter);
		mapRows.getChildren().clear();
		Path path = currentPath(mapField);
		mapFileLabel.setText(path == null ? "event-map.txt" : displayPath(path));
		EventMap map = null;
		if (path != null && Files.isRegularFile(path)) {
			try {
				map = EventMap.read(path);
			} catch (IOException | ScrambleException e) {
				mapRows.getChildren().add(emptyRow(e.getMessage()));
			}
		}
		if (map == null) {
			mapNames.setText("0");
			mapEvents.setText("0");
			mapNui.setText("0");
			if (mapRows.getChildren().isEmpty()) {
				mapRows.getChildren().add(emptyRow("No event map yet. Scramble writes one beside the resources folder."));
			}
			return;
		}
		int nui = 0;
		String query = mapSearch.getText() == null ? "" : mapSearch.getText().trim().toLowerCase();
		List<String> originals = new ArrayList<String>(map.originalToScrambled.keySet());
		Collections.sort(originals, String.CASE_INSENSITIVE_ORDER);
		int visible = 0;
		for (String original : originals) {
			boolean isNui = map.nuiOriginals.contains(original);
			if (isNui) {
				nui++;
			}
			if (mapFilter == 1 && isNui) {
				continue;
			}
			if (mapFilter == 2 && !isNui) {
				continue;
			}
			String scrambled = map.originalToScrambled.get(original);
			if (!query.isEmpty() && original.toLowerCase().indexOf(query) < 0 && scrambled.toLowerCase().indexOf(query) < 0) {
				continue;
			}
			mapRows.getChildren().add(mapRow(original, scrambled, isNui));
			visible++;
		}
		mapNames.setText(Integer.toString(map.originalToScrambled.size()));
		mapEvents.setText(Integer.toString(map.originalToScrambled.size() - nui));
		mapNui.setText(Integer.toString(nui));
		if (visible == 0) {
			mapRows.getChildren().add(emptyRow("Nothing in the map matches that search."));
		}
	}

	private void refreshHistory() {
		if (historyRows == null) {
			return;
		}
		markSegments(historyFilters, historyFilter);
		List<RunHistory> runs = RunHistory.read(projectRoot);
		int scrambles = 0;
		int unscrambles = 0;
		int failed = 0;
		for (RunHistory run : runs) {
			if (!run.success) {
				failed++;
			} else if ("Unscramble".equals(run.action)) {
				unscrambles++;
			} else {
				scrambles++;
			}
		}
		historySummary.setText(runs.size() + " runs   ·   " + scrambles + " scrambles   ·   " + unscrambles + " unscrambles   ·   " + failed + " failed");
		historyRows.getChildren().clear();
		int shown = 0;
		for (RunHistory run : runs) {
			if (historyFilter == 1 && !"Scramble".equals(run.action)) {
				continue;
			}
			if (historyFilter == 2 && !"Unscramble".equals(run.action)) {
				continue;
			}
			if (historyFilter == 3 && run.success) {
				continue;
			}
			historyRows.getChildren().add(historyCard(run));
			shown++;
		}
		if (shown == 0) {
			historyRows.getChildren().add(emptyRow(runs.isEmpty() ? "No runs yet. Scramble or unscramble and the result is kept here." : "Nothing in history matches that filter."));
		}
	}

	private HBox exclusionRow(ListView<String> list, String item) {
		boolean builtin = list == resourceList && item.equalsIgnoreCase(BUILTIN_RESOURCE);
		Label name = new Label(item);
		name.getStyleClass().add(builtin ? "builtin-name" : "row-name");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label source = new Label(builtin ? "Built-in" : sourceOf(list, item));
		source.getStyleClass().add(builtin ? "pill-builtin" : "Default".equals(source.getText()) ? "pill" : "pill-custom");
		HBox row = new HBox(12, name, spacer, source);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("table-row");
		if (!builtin) {
			Button remove = new Button("\u00d7");
			remove.getStyleClass().add("row-x");
			remove.setOnAction(event -> {
				list.getItems().remove(item);
				setStatus("Removed \"" + item + "\".");
			});
			row.getChildren().add(remove);
		}
		return row;
	}

	private HBox chip(ListView<String> list, String item) {
		boolean builtin = list == resourceList && item.equalsIgnoreCase(BUILTIN_RESOURCE);
		Label name = new Label((builtin ? "\u25B2  " : "") + item);
		name.getStyleClass().add("chip-name");
		HBox chip = new HBox(6, name);
		chip.setAlignment(Pos.CENTER_LEFT);
		chip.getStyleClass().add(builtin ? "chip-lock" : "chip");
		if (!builtin) {
			Button remove = new Button("\u00d7");
			remove.getStyleClass().add("chip-x");
			remove.setOnAction(event -> list.getItems().remove(item));
			chip.getChildren().add(remove);
		}
		return chip;
	}

	private HBox mapRow(String original, String scrambled, boolean nui) {
		Label from = new Label(original);
		from.getStyleClass().add("row-name");
		Label arrow = new Label("\u2192");
		arrow.getStyleClass().add("tiny");
		HBox fromCol = new HBox(8, from, arrow);
		fromCol.setAlignment(Pos.CENTER_LEFT);
		fromCol.setPrefWidth(360);
		fromCol.setMinWidth(220);
		Label to = new Label(scrambled);
		to.getStyleClass().add("scrambled-name");
		to.setPrefWidth(200);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label type = new Label(nui ? "NUI" : "Event");
		type.getStyleClass().add(nui ? "pill-nui" : "pill");
		type.setPrefWidth(72);
		Button copy = link("Copy");
		copy.setOnAction(event -> copyText(original + "\t" + scrambled));
		HBox row = new HBox(10, fromCol, to, spacer, type, copy);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("table-row");
		return row;
	}

	private HBox mapHead() {
		Label original = new Label("ORIGINAL");
		Label scrambled = new Label("SCRAMBLED");
		Label type = new Label("TYPE");
		original.getStyleClass().add("column-head");
		scrambled.getStyleClass().add("column-head");
		type.getStyleClass().add("column-head");
		original.setPrefWidth(360);
		original.setMinWidth(220);
		scrambled.setPrefWidth(200);
		type.setPrefWidth(72);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox head = new HBox(10, original, scrambled, spacer, type);
		head.getStyleClass().add("table-head");
		return head;
	}

	private VBox historyCard(RunHistory run) {
		Label kind = new Label(run.action);
		kind.getStyleClass().add("section-label");
		Label state = new Label(run.success ? "Completed" : "Failed");
		state.getStyleClass().add(run.success ? "badge-on" : "badge-bad");
		Label when = new Label(run.whenLabel());
		when.getStyleClass().add("tiny");
		HBox top = new HBox(8, kind, state, when);
		top.setAlignment(Pos.CENTER_LEFT);
		Label detail = new Label(run.detail);
		detail.getStyleClass().add("muted");
		detail.setWrapText(true);
		VBox text = new VBox(4, top, detail);
		HBox.setHgrow(text, Priority.ALWAYS);
		VBox counts = new VBox(2, countLine(run.names, "names"), countLine(run.files, "files"));
		counts.setAlignment(Pos.CENTER_RIGHT);
		HBox card = new HBox(12, text, counts);
		card.setAlignment(Pos.CENTER_LEFT);
		card.getStyleClass().add("card");
		VBox wrap = new VBox(card);
		return wrap;
	}

	private void addFromFilter() {
		if (addEntry(listFor(exclusionKind), exclusionFilter.getText())) {
			exclusionFilter.clear();
		}
	}

	private void changeRoot() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Project root");
		if (Files.isDirectory(projectRoot)) {
			chooser.setInitialDirectory(projectRoot.toFile());
		}
		java.io.File chosen = chooser.showDialog(stage);
		if (chosen == null) {
			return;
		}
		projectRoot = chosen.toPath().toAbsolutePath().normalize();
		resetPathsToRoot();
		loadConfigIntoLists(false);
	}

	private void resetPathsToRoot() {
		resourcesField.setText("./resources");
		configField.setText("./config");
		eventsField.setText("./RES_Anticheat/events.lua");
		mapField.setText("./event-map.txt");
		refreshDynamic();
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
				savedSnapshot = snapshot();
				setStatus("Loaded " + displayPath(config));
			} else if (reportMissing) {
				setStatus("No config at " + config.toAbsolutePath() + ". Save config to create it.");
				markDirty("The default lists are not written to the config file yet.");
			} else {
				fillLists(ScramblerConfig.defaultConfig());
				savedSnapshot = "";
				markDirty("The default lists are not written to the config file yet.");
			}
		} catch (IOException e) {
			showError("Could not read the config", e.getMessage());
		}
		refreshDynamic();
	}

	private void fillLists(ScramblerConfig config) {
		suppressDirty = true;
		try {
			replaceList(resourceList, config.blacklistedResources);
			replaceList(eventList, config.blacklistedEvents);
			replaceList(fileList, config.blacklistedFiles);
		} finally {
			suppressDirty = false;
		}
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

	private void addFileFromResources(ListView<String> list) {
		Path resources = currentPath(resourcesField);
		if (resources == null || !Files.isDirectory(resources)) {
			showError("Resources folder", "Choose the resources folder before adding a file from it.");
			return;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("File inside resources");
		chooser.setInitialDirectory(resources.toFile());
		java.io.File chosen = chooser.showOpenDialog(stage);
		if (chosen == null) {
			return;
		}
		Path file = chosen.toPath().toAbsolutePath().normalize();
		Path root = resources.toAbsolutePath().normalize();
		if (!file.startsWith(root)) {
			showError("File is outside resources", "Choose a file inside " + root);
			return;
		}
		addEntry(list, root.relativize(file).toString().replace('\\', '/'));
	}

	private boolean saveConfig(boolean announce) {
		Path config = currentPath(configField);
		if (config == null) {
			showError("Config file", "Enter a path for the config file.");
			return false;
		}
		try {
			ScramblerConfig.save(config, resourceList.getItems(), eventList.getItems(), fileList.getItems());
			savedSnapshot = snapshot();
			refreshDynamic();
			if (announce) {
				setStatus("Saved " + displayPath(config));
			}
			return true;
		} catch (IOException e) {
			showError("Could not save the config", e.getMessage());
			return false;
		}
	}

	private void scramble(Stage owner) {
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
			showError("Backup already exists", "A backup already exists at " + backup + ".\n\nUnscramble to restore the original names, or delete that backup before scrambling again.");
			return;
		}
		if (!confirm(owner, "Scramble resources", "Rewrite event names in " + displayPath(resources) + "?", "A clean copy will be saved to " + backup.getFileName() + ". The event map will be written to " + displayPath(map) + ".")) {
			return;
		}
		if (!saveConfig(false)) {
			return;
		}
		runJob("Scrambling resources...", "Scramble", () -> ResourceScrambler.run(resources, config, events, map));
	}

	private void unscramble(Stage owner) {
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
			showError("Event map", "Could not find the event map at " + map.toAbsolutePath() + ".\nScramble the resources first, and keep that file.");
			return;
		}
		if (!confirm(owner, "Unscramble resources", "Restore the original event names in " + displayPath(resources) + "?", "Names are restored from " + displayPath(map) + ". The anticheat event list is cleared when files change. The clean backup folder is left in place.")) {
			return;
		}
		if (!saveConfig(false)) {
			return;
		}
		runJob("Restoring original event names...", "Unscramble", () -> ResourceScrambler.unscramble(resources, config, events, map));
	}

	private void runJob(String busyMessage, String action, Job job) {
		setBusy(true, busyMessage);
		log.appendText("\n> " + action + "\n");
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
			String detail = action + " finished. " + outcome.mapping.size() + " names in " + outcome.filesChanged + " files.";
			setStatus(detail);
			RunHistory.append(projectRoot, action, true, outcome.mapping.size(), outcome.filesChanged, detail);
			refreshDynamic();
		});
		task.setOnFailed(event -> {
			setBusy(false, null);
			Throwable error = task.getException();
			String message = error.getMessage() == null ? error.toString() : error.getMessage();
			log.appendText(message + "\n");
			setStatus(action + " failed.");
			RunHistory.append(projectRoot, action, false, 0, 0, message);
			refreshDynamic();
			showError(action + " failed", message);
		});
		Thread worker = new Thread(task, "resource-event-scrambler");
		worker.setDaemon(true);
		worker.start();
	}

	private void chooseResources() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Resources folder");
		Path current = currentPath(resourcesField);
		if (current != null && Files.isDirectory(current)) {
			chooser.setInitialDirectory(current.toFile());
		}
		java.io.File chosen = chooser.showDialog(stage);
		if (chosen != null) {
			resourcesField.setText(toField(chosen.toPath()));
			refreshDynamic();
		}
	}

	private void chooseConfig() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Config file");
		prepareChooser(chooser, currentPath(configField), "config");
		java.io.File chosen = chooser.showOpenDialog(stage);
		if (chosen != null) {
			configField.setText(toField(chosen.toPath()));
			loadConfigIntoLists(true);
		}
	}

	private void chooseMap() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Event map");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
		prepareChooser(chooser, currentPath(mapField), "event-map.txt");
		java.io.File chosen = chooser.showSaveDialog(stage);
		if (chosen != null) {
			mapField.setText(toField(chosen.toPath()));
			refreshDynamic();
		}
	}

	private void chooseEvents() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Anticheat events.lua");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lua files", "*.lua"));
		prepareChooser(chooser, currentPath(eventsField), "events.lua");
		java.io.File chosen = chooser.showSaveDialog(stage);
		if (chosen != null) {
			eventsField.setText(toField(chosen.toPath()));
			refreshDynamic();
		}
	}

	private void prepareChooser(FileChooser chooser, Path current, String fallbackName) {
		if (current != null && current.getParent() != null && Files.isDirectory(current.getParent())) {
			chooser.setInitialDirectory(current.getParent().toFile());
			if (current.getFileName() != null) {
				chooser.setInitialFileName(current.getFileName().toString());
			}
		} else if (Files.isDirectory(projectRoot)) {
			chooser.setInitialDirectory(projectRoot.toFile());
			chooser.setInitialFileName(fallbackName);
		}
	}

	private ScrollPane page(String id, VBox content) {
		content.setPadding(new Insets(28, 32, 28, 32));
		content.setFillWidth(true);
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setId(id);
		scroll.getStyleClass().add("page");
		return scroll;
	}

	private HBox heading(Label title, String subtitle, Node actions) {
		Label sub = new Label(subtitle);
		sub.getStyleClass().add("page-sub");
		sub.setWrapText(true);
		VBox text = new VBox(4, title, sub);
		HBox.setHgrow(text, Priority.ALWAYS);
		HBox row = new HBox(16, text, actions);
		row.setAlignment(Pos.TOP_LEFT);
		return row;
	}

	private VBox statCard(Label count, String name, String detail, int kind) {
		count.getStyleClass().add("stat-count");
		Label title = new Label(name);
		title.getStyleClass().add("section-label");
		Label body = new Label(detail);
		body.getStyleClass().add("tiny");
		body.setWrapText(true);
		VBox card = new VBox(4, title, count, body);
		card.getStyleClass().addAll("card", "selectable");
		card.setOnMouseClicked(event -> {
			exclusionKind = kind;
			refreshExclusions();
		});
		return card;
	}

	private Button chipSegment(String name, int kind) {
		Button button = new Button(name);
		button.getStyleClass().add("segment");
		button.setOnAction(event -> {
			chipKind = kind;
			refreshChips();
		});
		return button;
	}

	private VBox pathCard(String name, String detail, TextField field, Label rel, Label state, String browseText, Runnable browse) {
		Label title = new Label(name);
		title.getStyleClass().add("section-label");
		Label body = new Label(detail);
		body.getStyleClass().add("muted");
		body.setWrapText(true);
		field.getStyleClass().add("path-field");
		Button browseButton = new Button(browseText);
		browseButton.getStyleClass().add("ghost");
		browseButton.setOnAction(event -> browse.run());
		formControls.add(field);
		formControls.add(browseButton);
		HBox input = new HBox(8, field, browseButton);
		input.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(field, Priority.ALWAYS);
		rel.getStyleClass().add("path-absolute");
		VBox card = new VBox(8, headerLine(title, state), body, input, rel);
		card.getStyleClass().add("card");
		return card;
	}

	private VBox step(String number, String title, String detail) {
		Label num = new Label(number);
		num.getStyleClass().add("step-num");
		Label name = new Label(title);
		name.getStyleClass().add("section-label");
		Label body = new Label(detail);
		body.getStyleClass().add("tiny");
		body.setWrapText(true);
		VBox card = new VBox(6, num, name, body);
		card.getStyleClass().add("step");
		return card;
	}

	private VBox metric(Label value, String caption) {
		value.getStyleClass().add("stat-count");
		Label cap = new Label(caption);
		cap.getStyleClass().add("tiny");
		cap.setWrapText(true);
		VBox card = new VBox(4, value, cap);
		card.getStyleClass().add("card");
		return card;
	}

	private VBox overviewPath(String name, Label rel, Label state) {
		Label title = new Label(name);
		title.getStyleClass().add("row-name");
		rel.getStyleClass().add("tiny");
		VBox text = new VBox(1, title, rel);
		HBox.setHgrow(text, Priority.ALWAYS);
		HBox row = new HBox(8, text, state);
		row.setAlignment(Pos.CENTER_LEFT);
		return new VBox(row);
	}

	private Button filterButton(String name, int filter) {
		Button button = new Button(name);
		button.getStyleClass().add("segment");
		button.setOnAction(event -> {
			mapFilter = filter;
			refreshMap();
		});
		return button;
	}

	private Button historyButton(String name, int filter) {
		Button button = new Button(name);
		button.getStyleClass().add("segment");
		button.setOnAction(event -> {
			historyFilter = filter;
			refreshHistory();
		});
		return button;
	}

	private Button actionButton(String label, String keys, String style) {
		Label text = new Label(label);
		text.getStyleClass().add("action-label");
		HBox graphic = new HBox(8, text, shortcut(keys));
		graphic.setAlignment(Pos.CENTER);
		Button button = new Button();
		button.setGraphic(graphic);
		button.getStyleClass().add(style);
		return button;
	}

	private Label shortcut(String keys) {
		Label label = new Label(keys);
		label.getStyleClass().add("shortcut");
		return label;
	}

	private Button link(String text) {
		Button button = new Button(text);
		button.getStyleClass().add("link");
		return button;
	}

	private Label eyebrow(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("eyebrow");
		return label;
	}

	private Label sectionTitle(String text, Node ignored) {
		Label label = new Label(text);
		label.getStyleClass().add("section-label");
		return label;
	}

	private HBox headerLine(Label title, Node trailing) {
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox row = new HBox(8, title, spacer, trailing);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Label emptyRow(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("empty");
		label.setWrapText(true);
		return label;
	}

	private Label countLine(int value, String noun) {
		Label label = new Label(value + "  " + noun);
		label.getStyleClass().add("count-line");
		return label;
	}

	private List<String> displayOrder(ListView<String> list) {
		List<String> items = new ArrayList<String>(list.getItems());
		if (list == resourceList) {
			Collections.sort(items, (left, right) -> {
				boolean leftBuiltIn = left.equalsIgnoreCase(BUILTIN_RESOURCE);
				boolean rightBuiltIn = right.equalsIgnoreCase(BUILTIN_RESOURCE);
				if (leftBuiltIn != rightBuiltIn) {
					return leftBuiltIn ? -1 : 1;
				}
				return left.compareToIgnoreCase(right);
			});
		}
		return items;
	}

	private ListView<String> listFor(int kind) {
		if (kind == 1) {
			return eventList;
		}
		if (kind == 2) {
			return fileList;
		}
		return resourceList;
	}

	private String sourceOf(ListView<String> list, String item) {
		ScramblerConfig defaults = ScramblerConfig.defaultConfig();
		if (list == resourceList && defaults.blacklistedResources.contains(item)) {
			return "Default";
		}
		if (list == eventList && defaults.blacklistedEvents.contains(item)) {
			return "Default";
		}
		if (list == fileList && defaults.isBlacklistedFile(item)) {
			return "Default";
		}
		return "Custom";
	}

	private String snapshot() {
		return join(resourceList) + "\n--\n" + join(eventList) + "\n--\n" + join(fileList);
	}

	private String join(ListView<String> list) {
		return String.join("\n", list.getItems());
	}

	private String displayPath(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		try {
			if (absolute.startsWith(projectRoot)) {
				String relative = projectRoot.relativize(absolute).toString().replace('\\', '/');
				return relative.isEmpty() ? "." : "./" + relative;
			}
		} catch (IllegalArgumentException ignored) {
			return absolute.toString();
		}
		return absolute.toString();
	}

	private void styleState(Label label, boolean found) {
		label.getStyleClass().removeAll("state-found", "state-pending");
		label.getStyleClass().add(found ? "state-found" : "state-pending");
	}

	private void selectStat(VBox card, boolean selected) {
		if (selected) {
			if (!card.getStyleClass().contains("stat-selected")) {
				card.getStyleClass().add("stat-selected");
			}
		} else {
			card.getStyleClass().remove("stat-selected");
		}
	}

	private void markDirty(String message) {
		savedSnapshot = "";
		unsavedDetail.setText(message);
		unsavedBox.setVisible(true);
		unsavedBox.setManaged(true);
	}

	private void copyMap() {
		Path path = currentPath(mapField);
		if (path == null || !Files.isRegularFile(path)) {
			showError("Event map", "There is no event map to copy yet.");
			return;
		}
		try {
			copyText(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			setStatus("Copied the event map.");
		} catch (IOException e) {
			showError("Could not copy the event map", e.getMessage());
		}
	}

	private void copyText(String text) {
		ClipboardContent content = new ClipboardContent();
		content.putString(text == null ? "" : text);
		Clipboard.getSystemClipboard().setContent(content);
	}

	private void openPath(Path path) {
		if (path == null || !Files.exists(path)) {
			showError("File", "That file is not on disk yet.");
			return;
		}
		try {
			java.awt.Desktop.getDesktop().open(path.toFile());
		} catch (Exception e) {
			showError("Could not open the file", path.toString());
		}
	}

	private Path currentPath(TextField field) {
		String text = field.getText();
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		String trimmed = text.trim();
		Path path = Paths.get(trimmed);
		if (!path.isAbsolute()) {
			if (trimmed.startsWith("./") || trimmed.startsWith(".\\")) {
				trimmed = trimmed.substring(2);
			}
			path = projectRoot.resolve(trimmed);
		}
		return path.toAbsolutePath().normalize();
	}

	private String toField(Path path) {
		return displayPath(path.toAbsolutePath().normalize());
	}

	private void markSegments(HBox box, int index) {
		if (box == null) {
			return;
		}
		for (int i = 0; i < box.getChildren().size(); i++) {
			Node node = box.getChildren().get(i);
			if (i == index) {
				if (!node.getStyleClass().contains("selected")) {
					node.getStyleClass().add("selected");
				}
			} else {
				node.getStyleClass().remove("selected");
			}
		}
	}

	private void setBusy(boolean busy, String busyMessage) {
		for (Node control : formControls) {
			control.setDisable(busy);
		}
		if (activity != null) {
			activity.setText(busy ? "Running" : "Idle");
		}
		if (busy && busyMessage != null) {
			setStatus(busyMessage);
		}
	}

	private void setStatus(String message) {
		if (message == null || message.isEmpty()) {
			return;
		}
		if (message.startsWith("Scramble") || message.startsWith("Unscramble") || message.startsWith("Restor") || message.startsWith("Saved")) {
			log.appendText(message + "\n");
		}
	}

	private boolean confirm(Stage owner, String title, String header, String message) {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.initOwner(owner);
		confirm.setTitle(title);
		confirm.setHeaderText(header);
		confirm.setContentText(message);
		styleDialog(confirm);
		return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	private void showError(String header, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		if (stage != null) {
			alert.initOwner(stage);
		}
		alert.setTitle("ResourceEventScrambler");
		alert.setHeaderText(header);
		alert.setContentText(message == null ? "" : message);
		styleDialog(alert);
		alert.showAndWait();
	}

	private void styleDialog(Alert alert) {
		DialogPane pane = alert.getDialogPane();
		URL css = ScramblerApp.class.getResource("scrambler.css");
		if (css != null && !pane.getStylesheets().contains(css.toExternalForm())) {
			pane.getStylesheets().add(css.toExternalForm());
		}
		pane.getStyleClass().add("app-dialog");
		if (pane.getContent() instanceof Label) {
			Label content = (Label) pane.getContent();
			content.setWrapText(true);
			content.setMaxWidth(480);
		}
		Node ok = pane.lookupButton(ButtonType.OK);
		if (ok != null) {
			ok.getStyleClass().add("scramble");
		}
		Node cancel = pane.lookupButton(ButtonType.CANCEL);
		if (cancel != null) {
			cancel.getStyleClass().add("ghost");
		}
	}

	private interface Job {
		ResourceScrambler.Outcome run() throws Exception;
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
