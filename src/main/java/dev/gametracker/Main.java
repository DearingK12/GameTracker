package dev.gametracker;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main extends Application {
    private final Database db = new Database();
    private final TableView<Game> table = new TableView<>();
    private final TableView<PlaySession> historyTable = new TableView<>();
    private final Label totalLabel = new Label("0 hrs");
    private final Label gamesLabel = new Label("0");
    private final Label weekLabel = new Label("0 hrs");
    private final Label monthLabel = new Label("0 hrs");
    private final Label sessionLabel = new Label("No active session");
    private final ComboBox<Game> sessionGame = new ComboBox<>();
    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final ObservableList<PlaySession> sessions = FXCollections.observableArrayList();
    private final ListView<Game> mostPlayed = new ListView<>();
    private Timeline processMonitor;
    private TrayIcon trayIcon;
    private boolean trayAvailable;
    private boolean manualSession;
    private boolean processDetectionWarningShown;
    private int activeSessionId = -1;
    private Game activeGame;

    @Override
    public void start(Stage stage) {
        db.initialize();
        db.recoverInterruptedSessions();
        stage.setTitle("Game Tracker");
        stage.setMinWidth(1000);
        stage.setMinHeight(720);

        Label brand = new Label("GAME TRACKER");
        brand.setFont(Font.font("Arial", 22));
        brand.setTextFill(Color.WHITE);
        Label subtitle = new Label("Your gaming time, all in one place");
        subtitle.setTextFill(Color.web("#9ca3af"));
        VBox header = new VBox(5, brand, subtitle);
        header.setPadding(new Insets(24));
        header.setStyle("-fx-background-color: #111827;");

        HBox stats = new HBox(14,
                statCard("LIFETIME PLAYTIME", totalLabel),
                statCard("GAMES TRACKED", gamesLabel),
                statCard("TRACKED THIS WEEK", weekLabel),
                statCard("TRACKED THIS MONTH", monthLabel));
        stats.setPadding(new Insets(20, 20, 8, 20));

        configureTables();
        VBox library = new VBox(12, sectionTitle("Your library"), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        library.setPadding(new Insets(12, 10, 20, 20));

        mostPlayed.setPrefHeight(170);
        mostPlayed.setPlaceholder(new Label("Add games to see your most-played list."));
        mostPlayed.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Game game, boolean empty) {
                super.updateItem(game, empty);
                if (empty || game == null) {
                    setText(null);
                } else {
                    setText(String.format("%s  ·  %,.1f hrs", game.getName(), game.getHours()));
                    setTextFill(Color.WHITE);
                }
            }
        });
        VBox insights = new VBox(12, sectionTitle("Most played"), mostPlayed,
                sectionTitle("Recent sessions"), historyTable);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        insights.setPadding(new Insets(12, 20, 20, 10));

        SplitPane content = new SplitPane(library, insights);
        content.setDividerPositions(0.64);
        VBox.setVgrow(content, Priority.ALWAYS);

        Button add = button("Add game");
        add.setOnAction(e -> addGameDialog());
        Button configureDetection = button("Configure detection");
        configureDetection.setOnAction(e -> configureDetectionDialog());
        Button addHours = button("Add historical hours");
        addHours.setOnAction(e -> historicalHoursDialog());
        Button importSteam = button("Import Steam hours");
        importSteam.setOnAction(e -> steamDialog());
        Label detectionHint = new Label("Auto-detection is on · configure an executable name when needed");
        detectionHint.setTextFill(Color.web("#9ca3af"));
        HBox actions = new HBox(10, add, configureDetection, addHours, importSteam, detectionHint);
        actions.setPadding(new Insets(0, 20, 12, 20));
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        sessionGame.setPromptText("Choose a game");
        sessionGame.setItems(games);
        Button start = button("Start manual session");
        start.setOnAction(e -> startManualSession());
        Button stop = button("Stop session");
        stop.setOnAction(e -> stopSession());
        Button quit = new Button("Quit");
        quit.setOnAction(e -> Platform.exit());
        HBox session = new HBox(10, new Label("SESSION"), sessionGame, start, stop, sessionLabel, quit);
        session.setPadding(new Insets(12, 20, 20, 20));
        session.setStyle("-fx-background-color: #111827;");
        session.getChildren().forEach(n -> {
            if (n instanceof Label label) label.setTextFill(Color.WHITE);
        });
        HBox.setHgrow(sessionLabel, Priority.ALWAYS);

        VBox root = new VBox(header, stats, actions, content, session);
        root.setStyle("-fx-background-color: #0b1020; -fx-font-family: Arial;");
        refresh();

        stage.setScene(new Scene(root, 1200, 820));
        trayAvailable = setupTray(stage);
        stage.setOnCloseRequest(event -> {
            if (trayAvailable) {
                event.consume();
                stage.hide();
            }
        });
        stage.show();

        processMonitor = new Timeline(new KeyFrame(Duration.seconds(10), event -> pollProcesses()));
        processMonitor.setCycleCount(Animation.INDEFINITE);
        processMonitor.play();
        pollProcesses();
    }

    private VBox statCard(String title, Label value) {
        Label heading = new Label(title);
        heading.setTextFill(Color.web("#9ca3af"));
        value.setFont(Font.font("Arial", 26));
        value.setTextFill(Color.WHITE);
        VBox box = new VBox(8, heading, value);
        box.setPadding(new Insets(18));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle("-fx-background-color: #182235; -fx-background-radius: 12;");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", 18));
        label.setTextFill(Color.WHITE);
        return label;
    }

    private Button button(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; "
                + "-fx-background-radius: 8; -fx-padding: 9 14;");
        return button;
    }

    private void configureTables() {
        TableColumn<Game, String> name = new TableColumn<>("GAME");
        name.setCellValueFactory(cell -> cell.getValue().nameProperty());
        name.setPrefWidth(220);
        TableColumn<Game, String> platform = new TableColumn<>("PLATFORM");
        platform.setCellValueFactory(cell -> cell.getValue().platformProperty());
        platform.setPrefWidth(115);
        TableColumn<Game, String> hours = new TableColumn<>("PLAYTIME");
        hours.setCellValueFactory(cell -> cell.getValue().hoursTextProperty());
        hours.setPrefWidth(100);
        TableColumn<Game, String> source = new TableColumn<>("HOURS SOURCE");
        source.setCellValueFactory(cell -> cell.getValue().sourceProperty());
        source.setPrefWidth(145);
        TableColumn<Game, String> process = new TableColumn<>("PROCESS MATCH");
        process.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getProcessMatch().isBlank() ? "Game name" : cell.getValue().getProcessMatch()));
        process.setPrefWidth(140);
        table.getColumns().setAll(name, platform, hours, source, process);
        table.setItems(games);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setStyle("-fx-background-color: #182235; -fx-control-inner-background: #182235; "
                + "-fx-text-background-color: white;");

        TableColumn<PlaySession, String> sessionGameColumn = new TableColumn<>("GAME");
        sessionGameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getGameName()));
        sessionGameColumn.setPrefWidth(125);
        TableColumn<PlaySession, String> started = new TableColumn<>("STARTED");
        started.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getStartedText()));
        started.setPrefWidth(130);
        TableColumn<PlaySession, String> duration = new TableColumn<>("TIME");
        duration.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getDurationText()));
        duration.setPrefWidth(70);
        historyTable.getColumns().setAll(sessionGameColumn, started, duration);
        historyTable.setItems(sessions);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("No sessions yet."));
        historyTable.setStyle("-fx-background-color: #182235; -fx-control-inner-background: #182235; "
                + "-fx-text-background-color: white;");
    }

    private void refresh() {
        games.setAll(db.getGames());
        double total = games.stream().mapToDouble(Game::getHours).sum();
        totalLabel.setText(String.format("%,.1f hrs", total));
        gamesLabel.setText(Integer.toString(games.size()));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli();
        long monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        weekLabel.setText(String.format("%,.1f hrs", db.getHoursBetween(weekStart, now)));
        monthLabel.setText(String.format("%,.1f hrs", db.getHoursBetween(monthStart, now)));

        mostPlayed.getItems().setAll(games.stream()
                .sorted(Comparator.comparingDouble(Game::getHours).reversed())
                .limit(5)
                .toList());
        sessions.setAll(db.getRecentSessions(8));
        sessionGame.setItems(games);
        updateSessionLabel();
    }

    private void addGameDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add game");
        TextField name = new TextField();
        name.setPromptText("Game name");
        ComboBox<String> platform = new ComboBox<>(FXCollections.observableArrayList(
                "Steam", "EA", "Battle.net", "Epic Games", "Ubisoft Connect", "GOG", "Other"));
        platform.setValue("Steam");
        TextField process = new TextField();
        process.setPromptText("Optional executable name(s), comma separated");
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, new Label("Game"), name);
        fields.addRow(1, new Label("Platform"), platform);
        fields.addRow(2, new Label("Process"), process);
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !name.getText().isBlank()) {
                db.addGame(name.getText().trim(), platform.getValue(), process.getText().trim());
                refresh();
            }
        });
    }

    private void configureDetectionDialog() {
        Game selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Select a game in your library first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getProcessMatch());
        dialog.setTitle("Configure game detection");
        dialog.setHeaderText("Process name for " + selected.getName());
        dialog.setContentText("Executable name(s), comma separated. Leave blank to use the game name:");
        dialog.showAndWait().ifPresent(value -> {
            db.updateProcessMatch(selected.id(), value.trim());
            refresh();
        });
    }

    private void historicalHoursDialog() {
        if (games.isEmpty()) {
            alert("Add a game first.");
            return;
        }
        ChoiceDialog<Game> choose = new ChoiceDialog<>(games.get(0), games);
        choose.setTitle("Historical playtime");
        choose.setHeaderText("Select the game to update");
        choose.setContentText("Game:");
        choose.showAndWait().ifPresent(game -> {
            TextInputDialog hours = new TextInputDialog("0");
            hours.setTitle("Historical playtime");
            hours.setHeaderText("Add hours already played");
            hours.setContentText("Hours to add:");
            hours.showAndWait().ifPresent(value -> {
                try {
                    double count = Double.parseDouble(value);
                    if (!Double.isFinite(count) || count < 0) throw new NumberFormatException();
                    db.addHours(game.id(), count, "Manual historical import");
                    refresh();
                } catch (NumberFormatException ex) {
                    alert("Enter a valid non-negative number.");
                }
            });
        });
    }

    private void steamDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Import Steam playtime");
        PasswordField key = new PasswordField();
        key.setPromptText("Steam Web API key");
        TextField id = new TextField();
        id.setPromptText("SteamID64");
        VBox fields = new VBox(8,
                new Label("Your Steam profile must allow access to owned-game data."),
                key, id);
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            try {
                int count = SteamImporter.importGames(key.getText().trim(), id.getText().trim(), db);
                refresh();
                alert("Imported/updated " + count + " Steam games.");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                alert("Steam import was interrupted.");
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                alert("Steam import failed: " + ex.getMessage());
            }
        });
    }

    private void startManualSession() {
        Game selected = sessionGame.getValue();
        if (selected == null) {
            alert("Choose a game first.");
            return;
        }
        if (activeSessionId != -1) {
            alert("A session is already running.");
            return;
        }
        startSession(selected, true, "Manual session");
    }

    private void startSession(Game game, boolean manual, String source) {
        activeSessionId = db.startSession(game.id(), System.currentTimeMillis(), source);
        activeGame = game;
        manualSession = manual;
        updateSessionLabel();
        refresh();
    }

    private void stopSession() {
        if (activeSessionId == -1) {
            alert("No active session.");
            return;
        }
        finishActiveSession(System.currentTimeMillis());
        refresh();
    }

    private void finishActiveSession(long endedAt) {
        if (activeSessionId == -1) return;
        db.finishSession(activeSessionId, endedAt);
        activeSessionId = -1;
        activeGame = null;
        manualSession = false;
        updateSessionLabel();
    }

    private void pollProcesses() {
        try {
            Set<String> running = ProcessHandle.allProcesses()
                    .map(process -> process.info().command().orElse(""))
                    .filter(command -> !command.isBlank())
                    .map(this::processName)
                    .filter(name -> !name.isBlank())
                    .collect(Collectors.toSet());

            if (activeSessionId != -1) {
                db.checkpointSession(activeSessionId, System.currentTimeMillis());
                if (manualSession) {
                    refresh();
                    return;
                }
            }

            Game detected = games.stream()
                    .filter(game -> matchesRunningProcess(game, running))
                    .findFirst()
                    .orElse(null);

            if (activeSessionId != -1 && (detected == null || detected.id() != activeGame.id())) {
                finishActiveSession(System.currentTimeMillis());
            }
            if (activeSessionId == -1 && detected != null) {
                startSession(detected, false, "Automatic process detection");
            }
            refresh();
        } catch (SecurityException ex) {
            if (!processDetectionWarningShown) {
                processDetectionWarningShown = true;
                alert("Game process detection is unavailable: " + ex.getMessage());
            }
        }
    }

    private boolean matchesRunningProcess(Game game, Set<String> running) {
        String configured = game.getProcessMatch();
        List<String> names = configured.isBlank()
                ? List.of(game.getName())
                : List.of(configured.split("[,;\\n]"));
        return names.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(this::normalizeProcessName)
                .anyMatch(running::contains);
    }

    private String processName(String command) {
        int separator = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        return normalizeProcessName(command.substring(separator + 1));
    }

    private String normalizeProcessName(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        lower = lower.replaceFirst("\\.(exe|app|bin|command|x86_64)$", "");
        return lower.replaceAll("[^a-z0-9]", "");
    }

    private void updateSessionLabel() {
        if (activeSessionId == -1 || activeGame == null) {
            sessionLabel.setText("No active session · auto-detection enabled");
        } else {
            String mode = manualSession ? "Manual tracking" : "Auto-detected";
            sessionLabel.setText(mode + ": " + activeGame.getName());
        }
    }

    private boolean setupTray(Stage stage) {
        if (!SystemTray.isSupported()) return false;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("Show Game Tracker");
            show.addActionListener(event -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));
            MenuItem quit = new MenuItem("Quit");
            quit.addActionListener(event -> Platform.runLater(Platform::exit));
            menu.add(show);
            menu.add(quit);
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setColor(new java.awt.Color(37, 99, 235));
            graphics.fillRoundRect(1, 1, 14, 14, 5, 5);
            graphics.dispose();
            trayIcon = new TrayIcon(image, "Game Tracker", menu);
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);
            Platform.setImplicitExit(false);
            return true;
        } catch (AWTException ex) {
            Platform.runLater(() -> alert("Could not create the background menu: " + ex.getMessage()));
            return false;
        }
    }

    private void alert(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }

    @Override
    public void stop() {
        if (processMonitor != null) processMonitor.stop();
        finishActiveSession(System.currentTimeMillis());
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
