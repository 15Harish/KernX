package com.linuxconsole.ui;

import com.linuxconsole.ssh.CommandResult;
import com.linuxconsole.ssh.ConnectionConfig;
import com.linuxconsole.ssh.SshService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Dark, terminal-styled dashboard. Equivalent of the Next.js Dashboard component +
 * app/api/execute/route.ts, but wired directly to SshService (no HTTP hop needed
 * since everything runs in one desktop process).
 */
public class DashboardView {

    private static final String BG = "#0d1117";
    private static final String PANEL = "#161b22";
    private static final String BORDER = "#30363d";
    private static final String GREEN = "#3fb950";
    private static final String TEXT = "#c9d1d9";
    private static final String ACCENT = "#58a6ff";

    private final SshService sshService = new SshService();

    private final TextField hostField = new TextField();
    private final TextField portField = new TextField("22");
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();
    private final TextField customCommandField = new TextField();
    private final TextArea outputArea = new TextArea();
    private final Label statusLabel = new Label("Not connected");
    private final CheckBox mockModeBox = new CheckBox("Mock mode (no server needed)");

    // Preset commands shown as buttons — extend this list as needed
    private final String[][] presetCommands = {
            {"Disk Usage", "df -h"},
            {"Memory", "free -m"},
            {"Uptime", "uptime"},
            {"Who Am I", "whoami"},
            {"System Info", "uname -a"},
            {"Processes", "ps aux"},
            {"Network Ports", "netstat -tulpn"},
            {"Current Dir", "pwd"}
    };

    public void start(Stage stage) {
        mockModeBox.setSelected(true);
        mockModeBox.selectedProperty().addListener((obs, oldVal, newVal) -> SshService.MOCK_MODE = newVal);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setPadding(new Insets(16));

        root.setTop(buildConnectionBar());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, 980, 680);
        stage.setTitle("Linux Command Center");
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildConnectionBar() {
        Label title = new Label("Linux Command Center");
        title.setTextFill(Color.web(GREEN));
        title.setFont(Font.font("Consolas", 22));

        hostField.setPromptText("Host / IP");
        portField.setPromptText("Port");
        portField.setPrefWidth(70);
        userField.setPromptText("Username");
        passField.setPromptText("Password");

        for (TextField f : new TextField[]{hostField, portField, userField, customCommandField}) {
            styleField(f);
        }
        styleField(passField);

        Button connectBtn = new Button("Connect");
        styleButton(connectBtn, ACCENT);
        connectBtn.setOnAction(e -> testConnection());

        statusLabel.setTextFill(Color.web("#8b949e"));
        statusLabel.setFont(Font.font("Consolas", 13));

        HBox connectionRow = new HBox(10, hostField, portField, userField, passField, connectBtn, mockModeBox, statusLabel);
        connectionRow.setAlignment(Pos.CENTER_LEFT);

        mockModeBox.setTextFill(Color.web(TEXT));

        VBox top = new VBox(10, title, connectionRow);
        top.setPadding(new Insets(0, 0, 16, 0));
        return top;
    }

    private BorderPane buildCenter() {
        BorderPane center = new BorderPane();

        // Left: preset command buttons
        VBox buttonPanel = new VBox(8);
        buttonPanel.setPadding(new Insets(0, 16, 0, 0));
        buttonPanel.setPrefWidth(200);

        Label presetLabel = new Label("COMMANDS");
        presetLabel.setTextFill(Color.web("#8b949e"));
        presetLabel.setFont(Font.font("Consolas", 12));
        buttonPanel.getChildren().add(presetLabel);

        for (String[] preset : presetCommands) {
            Button btn = new Button(preset[0]);
            btn.setMaxWidth(Double.MAX_VALUE);
            styleButton(btn, PANEL);
            btn.setOnAction(e -> runCommand(preset[1]));
            buttonPanel.getChildren().add(btn);
        }

        // Custom command row
        Label customLabel = new Label("CUSTOM COMMAND");
        customLabel.setTextFill(Color.web("#8b949e"));
        customLabel.setFont(Font.font("Consolas", 12));
        customCommandField.setPromptText("e.g. cat /etc/os-release");
        Button runCustomBtn = new Button("Run");
        styleButton(runCustomBtn, GREEN);
        runCustomBtn.setOnAction(e -> runCommand(customCommandField.getText()));
        HBox customRow = new HBox(8, customCommandField, runCustomBtn);
        HBox.setHgrow(customCommandField, Priority.ALWAYS);

        VBox.setMargin(customLabel, new Insets(16, 0, 0, 0));
        buttonPanel.getChildren().addAll(customLabel, customRow);

        // Right: terminal-style output
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle(
                "-fx-control-inner-background: #010409;" +
                "-fx-text-fill: " + GREEN + ";" +
                "-fx-font-family: 'Consolas', 'Monospaced';" +
                "-fx-font-size: 13px;" +
                "-fx-highlight-fill: #264f78;"
        );
        outputArea.setText("$ Linux Command Center ready. Mock mode is ON.\n$ Enter connection details and pick a command.\n");

        VBox outputBox = new VBox(6);
        Label outLabel = new Label("OUTPUT");
        outLabel.setTextFill(Color.web("#8b949e"));
        outLabel.setFont(Font.font("Consolas", 12));
        outputBox.getChildren().addAll(outLabel, outputArea);
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        center.setLeft(buttonPanel);
        center.setCenter(outputBox);
        return center;
    }

    private void testConnection() {
        appendOutput("$ Testing connection to " + hostField.getText() + "...");
        runCommand("whoami");
    }

    private void runCommand(String command) {
        if (command == null || command.isBlank()) return;

        if (!SshService.MOCK_MODE && hostField.getText().isBlank()) {
            appendOutput("! Enter a host/IP first (or enable Mock mode).");
            return;
        }

        appendOutput("\n$ " + command);
        statusLabel.setText("Running...");

        // Run off the UI thread so the app doesn't freeze during the SSH call
        Thread worker = new Thread(() -> {
            ConnectionConfig config = new ConnectionConfig(
                    hostField.getText().trim(),
                    parsePort(portField.getText()),
                    userField.getText().trim(),
                    passField.getText()
            );

            CommandResult result = sshService.executeCommand(config, command);

            Platform.runLater(() -> {
                if (result.isSuccess()) {
                    appendOutput(result.getOutput());
                    statusLabel.setText("Connected");
                } else {
                    appendOutput("! Error: " + result.getErrorMessage());
                    statusLabel.setText("Connection failed");
                }
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void appendOutput(String text) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        outputArea.appendText("\n[" + timestamp + "] " + text);
    }

    private int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return 22;
        }
    }

    private void styleField(TextField field) {
        field.setStyle(
                "-fx-background-color: " + PANEL + ";" +
                "-fx-text-fill: " + TEXT + ";" +
                "-fx-prompt-text-fill: #6e7681;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-font-family: 'Consolas', 'Monospaced';"
        );
    }

    private void styleButton(Button button, String bgColor) {
        button.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-text-fill: " + (bgColor.equals(PANEL) ? TEXT : "#ffffff") + ";" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-font-family: 'Consolas', 'Monospaced';" +
                "-fx-cursor: hand;"
        );
    }
}
