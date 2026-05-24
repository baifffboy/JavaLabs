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
            // Ширина - 50% от общей
            nameColumn.prefWidthProperty().bind(tableView.widthProperty().multiply(0.5));

            TableColumn<Player, Integer> winsColumn = new TableColumn<>("Число побед");
            winsColumn.setCellValueFactory(cellData ->
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getWins()).asObject());
            winsColumn.setStyle("-fx-alignment: CENTER;");
            // Ширина - 50% от общей
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

            // Принудительно фокусируем окно
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
        buttonBox.getChildren().addAll(createRefreshButton(tableView), createCloseButton());
        return buttonBox;
    }

    private static void loadTableDataAsync(TableView<Player> tableView) {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                List<Player> allPlayers = DatabaseService.getAllPlayers();

                List<Player> displayPlayers;
                if (allPlayers.isEmpty()) {
                    // Если данных нет, показываем двух игроков с 0 побед
                    displayPlayers = getDefaultPlayers();
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
                    // При ошибке показываем пустых игроков
                    ObservableList<Player> items = FXCollections.observableArrayList(getDefaultPlayers());
                    tableView.setItems(items);
                    tableView.setPlaceholder(new Label("Ошибка подключения к БД, показаны тестовые данные"));
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

    private static List<Player> getDefaultPlayers() {
        List<Player> defaultPlayers = new ArrayList<>();
        defaultPlayers.add(new Player("Игрок 1", 0));
        defaultPlayers.add(new Player("Игрок 2", 0));
        return defaultPlayers;
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

    public static boolean isOpen() {
        return isOpen;
    }
}