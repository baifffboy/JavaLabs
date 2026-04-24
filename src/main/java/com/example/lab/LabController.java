package com.example.lab;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LabController {
    @FXML private Circle circleBig;
    @FXML private Circle circleSmall;
    @FXML private AnchorPane pane;
    @FXML private Line lineSmall;
    @FXML private Line lineBig;
    @FXML private Line arrow;      // Стрела игрока 1
    @FXML private Line arrow1;     // Стрела игрока 2
    @FXML private Polyline archer;
    @FXML private Polyline archer1;
    @FXML private Pane counter;
    @FXML private Label counterOfShot1;
    @FXML private Label counterOfShot2;
    @FXML private Label count1;
    @FXML private Label count2;
    @FXML private Label nameOfGamer1;
    @FXML private Label nameOfGamer2;
    @FXML private Button ready;
    @FXML private Button stop;
    @FXML private Button shot;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerName;
    private int playerId;
    private boolean gameActive = false;
    private Stage statusStage;
    private Label statusLabel;

    private String otherPlayerName = "";
    private int otherPlayerScore = 0;
    private int otherPlayerShots = 0;

    Thread threadForCircle;
    Thread threadForArrow;
    volatile boolean isRun;
    volatile boolean isPause;
    volatile boolean isFly;
    private final Object lockObject = new Object();

    private static final int STEP_BY_BIGCIRCLE = 5;
    private static final int STEP_BY_SMALLCIRCLE = STEP_BY_BIGCIRCLE * 2;
    private static final int STEP_BY_ARROW = 10;
    private int myScore = 0;
    private int myShots = 0;
    private double LENGTH_OF_ARROW;

    private static final double ARROW1_START_X = 164;
    private static final double ARROW1_START_Y = 77;
    private static final double ARROW2_START_X = 163;
    private static final double ARROW2_START_Y = 242;

    private class Point {
        double x;
        double y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private AtomicReference<Point> currentPointForCircleBig;
    private AtomicReference<Point> currentPointForCircleSmall;
    private AtomicReference<Point> currentPointForMyArrow;

    @FXML
    public void initialize() {
        currentPointForCircleBig = new AtomicReference<>(new Point(circleBig.getLayoutX(), circleBig.getLayoutY()));
        currentPointForCircleSmall = new AtomicReference<>(new Point(circleSmall.getLayoutX(), circleSmall.getLayoutY()));

        LENGTH_OF_ARROW = arrow.getEndX() - arrow.getStartX();

        AnimationTimer renderer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                Point pBig = currentPointForCircleBig.get();
                circleBig.setLayoutX(pBig.x);
                circleBig.setLayoutY(pBig.y);

                Point pSmall = currentPointForCircleSmall.get();
                circleSmall.setLayoutX(pSmall.x);
                circleSmall.setLayoutY(pSmall.y);

                if (currentPointForMyArrow != null) {
                    Point pArrow = currentPointForMyArrow.get();
                    if (playerId == 1) {
                        arrow.setLayoutX(pArrow.x);
                        arrow.setLayoutY(pArrow.y);
                    } else {
                        arrow1.setLayoutX(pArrow.x);
                        arrow1.setLayoutY(pArrow.y);
                    }
                }
            }
        };
        renderer.start();

        Platform.runLater(() -> showConnectionDialog());
    }

    private void showStatusWindow(String title, String message) {
        Platform.runLater(() -> {
            if (statusStage != null && statusStage.isShowing()) {
                statusLabel.setText(message);
                return;
            }

            statusStage = new Stage();
            statusStage.initModality(Modality.NONE);
            statusStage.initStyle(StageStyle.UTILITY);
            statusStage.setTitle(title);

            statusLabel = new Label(message);
            statusLabel.setStyle("-fx-font-size: 14px; -fx-padding: 20px;");
            statusLabel.setAlignment(Pos.CENTER);

            VBox vbox = new VBox(statusLabel);
            vbox.setStyle("-fx-padding: 10px; -fx-alignment: center;");

            Scene scene = new Scene(vbox, 400, 150);
            statusStage.setScene(scene);
            statusStage.show();
        });
    }

    private void closeStatusWindow() {
        Platform.runLater(() -> {
            if (statusStage != null && statusStage.isShowing()) {
                statusStage.close();
                statusStage = null;
            }
        });
    }

    private void showErrorWindow(String message) {
        Platform.runLater(() -> {
            Stage errorStage = new Stage();
            errorStage.initModality(Modality.APPLICATION_MODAL);
            errorStage.setTitle("Сообщение");

            Label errorLabel = new Label(message);
            errorLabel.setStyle("-fx-font-size: 14px; -fx-padding: 20px;");
            errorLabel.setAlignment(Pos.CENTER);

            Button okButton = new Button("OK");
            okButton.setOnAction(e -> errorStage.close());

            VBox vbox = new VBox(errorLabel, okButton);
            vbox.setStyle("-fx-padding: 20px; -fx-alignment: center; -fx-spacing: 10px;");

            Scene scene = new Scene(vbox, 400, 200);
            errorStage.setScene(scene);
            errorStage.showAndWait();
        });
    }

    private void showConnectionDialog() {
        TextInputDialog numDialog = new TextInputDialog("1");
        numDialog.setTitle("Номер игрока");
        numDialog.setHeaderText("Выберите номер игрока (1 или 2)");
        numDialog.setContentText("Номер:");

        Optional<String> numResult = numDialog.showAndWait();
        if (numResult.isPresent()) {
            try {
                playerId = Integer.parseInt(numResult.get());
                if (playerId != 1 && playerId != 2) playerId = 1;
            } catch (NumberFormatException e) {
                playerId = 1;
            }
        }

        TextInputDialog dialog = new TextInputDialog("Игрок" + playerId);
        dialog.setTitle("Подключение");
        dialog.setHeaderText("Введите имя игрока");
        dialog.setContentText("Имя:");

        Optional<String> result = dialog.showAndWait();
        playerName = result.orElse("Игрок" + playerId);
        if (playerName.trim().isEmpty()) playerName = "Игрок" + playerId;

        connectToServer();
    }

    private void connectToServer() {
        showStatusWindow("Подключение", "Подключение к серверу...");

        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 12345);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println(playerName + ":" + playerId);
                String response = in.readLine();

                if (response != null && response.startsWith("OK:")) {
                    int assignedId = Integer.parseInt(response.split(":")[1]);
                    playerId = assignedId;

                    if (playerId == 1) {
                        currentPointForMyArrow = new AtomicReference<>(new Point(ARROW1_START_X, ARROW1_START_Y));
                        Platform.runLater(() -> {
                            nameOfGamer1.setText(playerName);
                            nameOfGamer2.setText("Ожидание...");
                        });
                    } else {
                        currentPointForMyArrow = new AtomicReference<>(new Point(ARROW2_START_X, ARROW2_START_Y));
                        Platform.runLater(() -> {
                            nameOfGamer2.setText(playerName);
                            nameOfGamer1.setText("Ожидание...");
                        });
                    }

                    Platform.runLater(() -> {
                        ready.setDisable(false);
                    });

                    showStatusWindow("Ожидание игроков", "Ожидание готовности других игроков...\nНажмите 'Готов', когда будете готовы");

                    String msg;
                    while ((msg = in.readLine()) != null) {
                        handleServerMessage(msg);
                    }
                } else if ("NAME_TAKEN".equals(response)) {
                    closeStatusWindow();
                    showErrorWindow("Имя уже занято!");
                    Platform.runLater(() -> showConnectionDialog());
                } else {
                    closeStatusWindow();
                    showErrorWindow("Ошибка подключения");
                }
            } catch (IOException e) {
                closeStatusWindow();
                showErrorWindow("Сервер не запущен!");
            }
        }).start();
    }

    private void handleServerMessage(String msg) {
        Platform.runLater(() -> {
            if (msg.equals("START")) {
                closeStatusWindow();
                gameActive = true;
                isPause = false;
                myScore = 0;
                myShots = 0;
                otherPlayerScore = 0;
                otherPlayerShots = 0;

                if (playerId == 1) {
                    currentPointForMyArrow.set(new Point(ARROW1_START_X, ARROW1_START_Y));
                    count1.setText("0");
                    counterOfShot1.setText("0");
                    count2.setText("0");
                    counterOfShot2.setText("0");
                } else {
                    currentPointForMyArrow.set(new Point(ARROW2_START_X, ARROW2_START_Y));
                    count2.setText("0");
                    counterOfShot2.setText("0");
                    count1.setText("0");
                    counterOfShot1.setText("0");
                }
                onStart();
            }
            else if (msg.startsWith("SCORE:")) {
                String[] parts = msg.split(":");
                int id = Integer.parseInt(parts[1]);
                int score = Integer.parseInt(parts[2]);
                int shots = Integer.parseInt(parts[3]);
                String name = parts[4];

                if (id == playerId) {
                    myScore = score;
                    myShots = shots;
                    if (playerId == 1) {
                        count1.setText(String.valueOf(score));
                        counterOfShot1.setText(String.valueOf(shots));
                    } else {
                        count2.setText(String.valueOf(score));
                        counterOfShot2.setText(String.valueOf(shots));
                    }
                } else {
                    otherPlayerScore = score;
                    otherPlayerShots = shots;
                    otherPlayerName = name;
                    if (playerId == 1) {
                        count2.setText(String.valueOf(score));
                        counterOfShot2.setText(String.valueOf(shots));
                        nameOfGamer2.setText(name);
                    } else {
                        count1.setText(String.valueOf(score));
                        counterOfShot1.setText(String.valueOf(shots));
                        nameOfGamer1.setText(name);
                    }
                }
            }
            else if (msg.startsWith("NEW_PLAYER:")) {
                String[] parts = msg.split(":");
                int id = Integer.parseInt(parts[1]);
                String name = parts[2];
                if (id != playerId) {
                    otherPlayerName = name;
                    if (playerId == 1) {
                        nameOfGamer2.setText(name);
                    } else {
                        nameOfGamer1.setText(name);
                    }
                }
            }
            else if (msg.startsWith("WINNER:")) {
                int winnerId = Integer.parseInt(msg.split(":")[1]);
                gameActive = false;
                isRun = false;
                String winnerName = (winnerId == playerId) ? playerName : otherPlayerName;
                showErrorWindow("Победитель: " + winnerName + "!");
                ready.setDisable(false);
                resetGame();
            }
            else if (msg.startsWith("STOP:")) {
                gameActive = false;
                isRun = false;
                resetGame();
                showErrorWindow(msg.substring(5));
                ready.setDisable(false);
            }
        });
    }

    void next() {
        currentPointForCircleBig.getAndUpdate(point -> {
            double ty = point.y;
            ty += STEP_BY_BIGCIRCLE;
            if (ty > lineBig.getEndY() + lineBig.getLayoutY()) ty = lineBig.getStartY() + lineBig.getLayoutY();
            return new Point(point.x, ty);
        });

        currentPointForCircleSmall.getAndUpdate(point -> {
            double ty = point.y;
            ty -= STEP_BY_SMALLCIRCLE;
            if (ty < lineSmall.getStartY() + lineSmall.getLayoutY()) ty = lineSmall.getEndY() + lineSmall.getLayoutY();
            return new Point(point.x, ty);
        });
    }

    void onStart() {
        if (threadForCircle != null) return;
        threadForCircle = new Thread(() -> {
            isRun = true;
            isPause = false;
            while (isRun && gameActive) {
                next();
                synchronized (lockObject) {
                    if (isPause) {
                        try {
                            lockObject.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        threadForCircle.start();
    }

    void resetGame() {
        isRun = false;
        isFly = false;

        if (threadForCircle != null) {
            threadForCircle.interrupt();
            threadForCircle = null;
        }
        if (threadForArrow != null) {
            threadForArrow.interrupt();
            threadForArrow = null;
        }

        if (playerId == 1) {
            currentPointForMyArrow.set(new Point(ARROW1_START_X, ARROW1_START_Y));
        } else {
            currentPointForMyArrow.set(new Point(ARROW2_START_X, ARROW2_START_Y));
        }

        if (currentPointForCircleBig != null && currentPointForCircleSmall != null) {
            currentPointForCircleBig.set(new Point(327, 154));
            currentPointForCircleSmall.set(new Point(406, 154));
        }
    }

    void nextFlyStep() {
        currentPointForMyArrow.getAndUpdate(point -> {
            double tx = point.x + arrow.getEndX();
            double ty = point.y;

            double startX = (playerId == 1) ? ARROW1_START_X : ARROW2_START_X;
            double startY = (playerId == 1) ? ARROW1_START_Y : ARROW2_START_Y;

            if (tx >= counter.getLayoutX() - LENGTH_OF_ARROW / 2) {
                isFly = false;
                return new Point(startX, startY);
            }

            // Попадание в большую мишень
            if (tx >= circleBig.getLayoutX() - circleBig.getRadius() && tx <= circleBig.getLayoutX() + circleBig.getRadius()
                    && ty >= circleBig.getLayoutY() - circleBig.getRadius() && ty <= circleBig.getLayoutY() + circleBig.getRadius()) {
                myScore++;
                // Отправляем на сервер только очки, выстрел уже был засчитан при нажатии
                if (out != null) out.println("SHOT:1");
                Platform.runLater(() -> {
                    if (playerId == 1) {
                        count1.setText(String.valueOf(myScore));
                    } else {
                        count2.setText(String.valueOf(myScore));
                    }
                });
                isFly = false;
                return new Point(startX, startY);
            }
            // Попадание в маленькую мишень
            else if (tx >= circleSmall.getLayoutX() - circleSmall.getRadius() && tx <= circleSmall.getLayoutX() + circleSmall.getRadius()
                    && ty >= circleSmall.getLayoutY() - circleSmall.getRadius() && ty <= circleSmall.getLayoutY() + circleSmall.getRadius()) {
                myScore += 2;
                if (out != null) out.println("SHOT:2");
                Platform.runLater(() -> {
                    if (playerId == 1) {
                        count1.setText(String.valueOf(myScore));
                    } else {
                        count2.setText(String.valueOf(myScore));
                    }
                });
                isFly = false;
                return new Point(startX, startY);
            } else {
                tx += STEP_BY_ARROW;
                return new Point(tx - arrow.getEndX(), point.y);
            }
        });
    }

    void arrowFlight() {
        if (isFly || !gameActive) return;

        if (threadForArrow != null) {
            threadForArrow.interrupt();
            threadForArrow = null;
        }

        if (playerId == 1) {
            currentPointForMyArrow.set(new Point(ARROW1_START_X, ARROW1_START_Y));
        } else {
            currentPointForMyArrow.set(new Point(ARROW2_START_X, ARROW2_START_Y));
        }

        isFly = true;
        threadForArrow = new Thread(() -> {
            while (isFly && gameActive) {
                nextFlyStep();
                synchronized (lockObject) {
                    if (isPause) {
                        try {
                            lockObject.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
            threadForArrow = null;
        });
        threadForArrow.start();
    }

    @FXML
    void onArcherClicked() {
        if (!gameActive || isFly) return;
        myShots++;
        // Отправляем выстрел на сервер для учета количества выстрелов
        if (out != null) out.println("SHOT_COUNT:" + myShots);
        if (playerId == 1) {
            counterOfShot1.setText(String.valueOf(myShots));
        } else {
            counterOfShot2.setText(String.valueOf(myShots));
        }
        arrowFlight();
    }

    @FXML
    void onReady() {
        if (out != null) {
            out.println("READY");
            ready.setDisable(true);
            showStatusWindow("Ожидание", "Ожидание готовности других игроков...");
        }
    }

    @FXML
    void onStop() {
        if (out != null && gameActive) {
            out.println("STOP");
        }
        resetGame();
        closeStatusWindow();
    }

    @FXML
    void onShot() {
        onArcherClicked();
    }

    public void shutdown() {
        isRun = false;
        isFly = false;
        if (threadForCircle != null) threadForCircle.interrupt();
        if (threadForArrow != null) threadForArrow.interrupt();
        if (socket != null) {
            try { socket.close(); } catch (IOException e) {}
        }
        closeStatusWindow();
    }
}