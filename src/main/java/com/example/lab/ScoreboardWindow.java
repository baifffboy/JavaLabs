package com.example.lab;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardWindow {
    private static Stage currentStage;
    private static boolean isOpen = false;
    private static Runnable onCloseCallback;
    private static String currentPlayer1Name = "Игрок 1";
    private static String currentPlayer2Name = "Игрок 2";
    private static LabController labController;

    public static void updateCurrentPlayerNames(String player1Name, String player2Name) {
        if (player1Name != null && !player1Name.trim().isEmpty()) {
            currentPlayer1Name = player1Name;
        }
        if (player2Name != null && !player2Name.trim().isEmpty()) {
            currentPlayer2Name = player2Name;
        }
    }

    public static void showAndWait(Runnable onClose) {
        if (isOpen) {
            if (onClose != null) onClose.run();
            return;
        }

        onCloseCallback = onClose;
        isOpen = true;

        if (Platform.isFxApplicationThread()) {
            createAndShowStage();
        } else {
            Platform.runLater(() -> createAndShowStage());
        }
    }

    private static void createAndShowStage() {
        try {
            Stage stage = new Stage();
            currentStage = stage;
            stage.initModality(Modality.NONE);
            stage.initStyle(StageStyle.DECORATED);
            stage.setTitle("Таблица рекордов");

            TableView<Player> tableView = new TableView<>();

            TableColumn<Player, String> nameColumn = new TableColumn<>("Имя игрока");
            nameColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getPlayerName()));
            nameColumn.prefWidthProperty().bind(tableView.widthProperty().multiply(0.5));

            TableColumn<Player, Integer> winsColumn = new TableColumn<>("Число побед");
            winsColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getWins()).asObject());
            winsColumn.setStyle("-fx-alignment: CENTER;");
            winsColumn.prefWidthProperty().bind(tableView.widthProperty().multiply(0.5));

            tableView.getColumns().addAll(nameColumn, winsColumn);
            tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            tableView.setPlaceholder(new Label("Загрузка данных..."));

            VBox vbox = new VBox(10, tableView, createButtons(tableView));
            vbox.setPadding(new Insets(15));
            vbox.setStyle("-fx-background-color: #f0f0f0;");

            Scene scene = new Scene(vbox, 500, 400);
            stage.setScene(scene);

            stage.setOnHidden(e -> {
                isOpen = false;
                currentStage = null;
                if (onCloseCallback != null) {
                    onCloseCallback.run();
                    onCloseCallback = null;
                }
            });

            stage.show();

            stage.toFront();
            stage.requestFocus();

            loadTableDataAsync(tableView);

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Ошибка при открытии таблицы: " + e.getMessage());
            isOpen = false;
            currentStage = null;
        }
    }

    private static Button createRefreshButton(TableView<Player> tableView) {
        Button refreshButton = new Button("Обновить");
        refreshButton.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");
        refreshButton.setOnAction(e -> loadTableDataAsync(tableView));
        return refreshButton;
    }

    private static Button createResetButton(TableView<Player> tableView) {
        Button resetButton = new Button("Сброс");
        resetButton.setStyle("-fx-font-size: 14px; -fx-padding: 10px; -fx-background-color: #ff4444; -fx-text-fill: white;");
        resetButton.setOnAction(e -> {
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("Подтверждение сброса");
            confirmDialog.setHeaderText("Вы уверены?");
            confirmDialog.setContentText("Это действие удалит всех игроков и добавит всех активных игроков из всех комнат с 0 побед.");

            ButtonType yesButton = new ButtonType("Да", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Нет", ButtonBar.ButtonData.NO);
            confirmDialog.getButtonTypes().setAll(yesButton, noButton);

            confirmDialog.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    LabController controller = getLabController();
                    if (controller != null) {
                        controller.onResetAllPlayers();
                    }
                }
            });
        });
        return resetButton;
    }

    private static LabController getLabController() {
        return labController;
    }

    public static void setLabController(LabController controller) {
        labController = controller;
    }

    private static Button createCloseButton() {
        Button closeButton = new Button("Закрыть таблицу");
        closeButton.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");
        closeButton.setOnAction(e -> {
            if (currentStage != null) {
                currentStage.close();
            }
        });
        return closeButton;
    }

    private static HBox createButtons(TableView<Player> tableView) {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(createRefreshButton(tableView), createResetButton(tableView), createCloseButton());
        return buttonBox;
    }

    private static void loadTableDataAsync(TableView<Player> tableView) {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                List<Player> allPlayers = DatabaseService.getAllPlayers();

                List<Player> displayPlayers;
                if (allPlayers.isEmpty()) {
                    displayPlayers = new ArrayList<>();
                    displayPlayers.add(new Player(currentPlayer1Name, 0));
                    displayPlayers.add(new Player(currentPlayer2Name, 0));
                    Platform.runLater(() -> {
                        tableView.setPlaceholder(new Label(""));
                    });
                } else {
                    displayPlayers = new ArrayList<>(allPlayers);
                }

                final List<Player> finalDisplayPlayers = displayPlayers;
                Platform.runLater(() -> {
                    ObservableList<Player> items = FXCollections.observableArrayList(finalDisplayPlayers);
                    tableView.setItems(items);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    List<Player> defaultPlayers = new ArrayList<>();
                    defaultPlayers.add(new Player(currentPlayer1Name, 0));
                    defaultPlayers.add(new Player(currentPlayer2Name, 0));
                    ObservableList<Player> items = FXCollections.observableArrayList(defaultPlayers);
                    tableView.setItems(items);
                    tableView.setPlaceholder(new Label("Ошибка подключения к БД, показаны текущие игроки"));
                });
            }
        }).start();
    }

    private static void showErrorDialog(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void close() {
        if (currentStage != null) {
            Platform.runLater(() -> {
                currentStage.close();
                currentStage = null;
                isOpen = false;
            });
        }
    }
}