package com.example.lab;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
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
    @FXML private Line arrow;
    @FXML private Line arrow1;
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
    private int playerNumber = 1;
    private boolean gameActive = false;

    Thread threadForCircle;
    Thread threadForArrow1;
    Thread threadForArrow2;
    volatile boolean isRun;
    volatile boolean isPause;
    volatile boolean isFly1;
    volatile boolean isFly2;
    private final Object lockObject = new Object();

    private static final int STEP_BY_BIGCIRCLE = 5;
    private static final int STEP_BY_SMALLCIRCLE = STEP_BY_BIGCIRCLE * 2;
    private static final int STEP_BY_ARROW = 10;
    private static int shot1 = 0;
    private static int shot2 = 0;
    private static int countPoints1 = 0;
    private static int countPoints2 = 0;
    private double LENGTH_OF_ARROW;

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
    private AtomicReference<Point> currentPointForArrow;
    private AtomicReference<Point> currentPointForArrow1;

    @FXML
    public void initialize() {
        currentPointForCircleBig = new AtomicReference<>(new Point(circleBig.getLayoutX(), circleBig.getLayoutY()));
        currentPointForCircleSmall = new AtomicReference<>(new Point(circleSmall.getLayoutX(), circleSmall.getLayoutY()));
        currentPointForArrow = new AtomicReference<>(new Point(arrow.getLayoutX(), arrow.getLayoutY()));
        currentPointForArrow1 = new AtomicReference<>(new Point(arrow1.getLayoutX(), arrow1.getLayoutY()));

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

                Point pArrow = currentPointForArrow.get();
                arrow.setLayoutX(pArrow.x);
                arrow.setLayoutY(pArrow.y);

                Point pArrow1 = currentPointForArrow1.get();
                arrow1.setLayoutX(pArrow1.x);
                arrow1.setLayoutY(pArrow1.y);
            }
        };
        renderer.start();

        Platform.runLater(() -> showConnectionDialog());
    }

    private void showConnectionDialog() {
        TextInputDialog numDialog = new TextInputDialog("1");
        numDialog.setTitle("Номер игрока");
        numDialog.setHeaderText("Вы игрок 1 или 2?");
        numDialog.setContentText("Номер (1 или 2):");

        Optional<String> numResult = numDialog.showAndWait();
        if (numResult.isPresent()) {
            try {
                playerNumber = Integer.parseInt(numResult.get());
                if (playerNumber != 1 && playerNumber != 2) playerNumber = 1;
            } catch (NumberFormatException e) {
                playerNumber = 1;
            }
        }

        TextInputDialog dialog = new TextInputDialog("Игрок" + playerNumber);
        dialog.setTitle("Подключение");
        dialog.setHeaderText("Введите имя игрока");
        dialog.setContentText("Имя:");

        Optional<String> result = dialog.showAndWait();
        playerName = result.orElse("Игрок" + playerNumber);
        if (playerName.trim().isEmpty()) playerName = "Игрок" + playerNumber;

        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println(playerName);
                String response = in.readLine();

                if ("OK".equals(response)) {
                    Platform.runLater(() -> {
                        if (playerNumber == 1) {
                            nameOfGamer1.setText(playerName);
                        } else {
                            nameOfGamer2.setText(playerName);
                        }
                        ready.setDisable(false);
                    });

                    while (true) {
                        String msg = in.readLine();
                        if (msg == null) break;
                        handleServerMessage(msg);
                    }
                } else if ("NAME_TAKEN".equals(response)) {
                    Platform.runLater(() -> {
                        Label status = new Label("Имя занято!");
                        counter.getChildren().add(status);
                        showConnectionDialog();
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    Label status = new Label("Ошибка подключения");
                    counter.getChildren().add(status);
                });
            }
        }).start();
    }

    private void handleServerMessage(String msg) {
        Platform.runLater(() -> {
            if (msg.equals("START")) {
                gameActive = true;
                isPause = false;
                if (playerNumber == 1) {
                    countPoints1 = 0;
                    shot1 = 0;
                    count1.setText("0");
                    counterOfShot1.setText("0");
                } else {
                    countPoints2 = 0;
                    shot2 = 0;
                    count2.setText("0");
                    counterOfShot2.setText("0");
                }
                onStart();
            }
            else if (msg.startsWith("SCORE:")) {
                String[] parts = msg.split(":");
                String player = parts[1];
                int score = Integer.parseInt(parts[2]);
                int shots = Integer.parseInt(parts[3]);
                if (player.equals(playerName)) {
                    if (playerNumber == 1) {
                        countPoints1 = score;
                        shot1 = shots;
                        count1.setText(String.valueOf(score));
                        counterOfShot1.setText(String.valueOf(shots));
                    } else {
                        countPoints2 = score;
                        shot2 = shots;
                        count2.setText(String.valueOf(score));
                        counterOfShot2.setText(String.valueOf(shots));
                    }
                }
            }
            else if (msg.startsWith("WINNER:")) {
                String winner = msg.split(":")[1];
                gameActive = false;
                isRun = false;
                Label status = new Label("Победитель: " + winner + "!");
                counter.getChildren().add(status);
                ready.setDisable(false);
            }
            else if (msg.startsWith("STOP:")) {
                gameActive = false;
                isRun = false;
                resetGame();
                Label status = new Label(msg);
                counter.getChildren().add(status);
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
            while (isRun) {
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
        isFly1 = false;
        isFly2 = false;

        if (threadForCircle != null) {
            threadForCircle.interrupt();
            threadForCircle = null;
        }
        if (threadForArrow1 != null) {
            threadForArrow1.interrupt();
            threadForArrow1 = null;
        }
        if (threadForArrow2 != null) {
            threadForArrow2.interrupt();
            threadForArrow2 = null;
        }

        if (currentPointForArrow != null) {
            currentPointForArrow.set(new Point(164, 77));
        }
        if (currentPointForArrow1 != null) {
            currentPointForArrow1.set(new Point(163, 242));
        }
        if (currentPointForCircleBig != null && currentPointForCircleSmall != null) {
            currentPointForCircleBig.set(new Point(327, 154));
            currentPointForCircleSmall.set(new Point(406, 154));
        }
    }

    void nextFlyStep(AtomicReference<Point> currentArrow, int playerNum, int startX, int startY) {
        currentArrow.getAndUpdate(point -> {
            double tx = point.x + arrow.getEndX();
            double ty = point.y;

            if (tx >= counter.getLayoutX() - LENGTH_OF_ARROW / 2) {
                if (playerNum == 1) isFly1 = false;
                else isFly2 = false;
                return new Point(startX, startY);
            }

            if (tx >= circleBig.getLayoutX() - circleBig.getRadius() && tx <= circleBig.getLayoutX() + circleBig.getRadius()
                    && ty >= circleBig.getLayoutY() - circleBig.getRadius() && ty <= circleBig.getLayoutY() + circleBig.getRadius()) {
                if (playerNum == 1) {
                    countPoints1++;
                    Platform.runLater(() -> count1.setText(String.valueOf(countPoints1)));
                    if (out != null) out.println("SHOT:1");
                } else {
                    countPoints2++;
                    Platform.runLater(() -> count2.setText(String.valueOf(countPoints2)));
                    if (out != null) out.println("SHOT:1");
                }
                if (playerNum == 1) isFly1 = false;
                else isFly2 = false;
                return new Point(startX, startY);
            }
            else if (tx >= circleSmall.getLayoutX() - circleSmall.getRadius() && tx <= circleSmall.getLayoutX() + circleSmall.getRadius()
                    && ty >= circleSmall.getLayoutY() - circleSmall.getRadius() && ty <= circleSmall.getLayoutY() + circleSmall.getRadius()) {
                if (playerNum == 1) {
                    countPoints1 += 2;
                    Platform.runLater(() -> count1.setText(String.valueOf(countPoints1)));
                    if (out != null) out.println("SHOT:2");
                } else {
                    countPoints2 += 2;
                    Platform.runLater(() -> count2.setText(String.valueOf(countPoints2)));
                    if (out != null) out.println("SHOT:2");
                }
                if (playerNum == 1) isFly1 = false;
                else isFly2 = false;
                return new Point(startX, startY);
            } else {
                tx += STEP_BY_ARROW;
                return new Point(tx - arrow.getEndX(), point.y);
            }
        });
    }

    void arrowFlight(int playerNum) {
        if (playerNum == 1) {
            if (threadForArrow1 != null) return;
            isFly1 = true;
            threadForArrow1 = new Thread(() -> {
                while (isFly1) {
                    nextFlyStep(currentPointForArrow, 1, 164, 77);
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
            });
            threadForArrow1.start();
        } else {
            if (threadForArrow2 != null) return;
            isFly2 = true;
            threadForArrow2 = new Thread(() -> {
                while (isFly2) {
                    nextFlyStep(currentPointForArrow1, 2, 163, 242);
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
            });
            threadForArrow2.start();
        }
    }

    @FXML
    void onArcherClicked() {
        if (!gameActive) return;
        if (playerNumber == 1) {
            shot1++;
            counterOfShot1.setText(String.valueOf(shot1));
            arrowFlight(1);
        } else {
            shot2++;
            counterOfShot2.setText(String.valueOf(shot2));
            arrowFlight(2);
        }
    }

    @FXML
    void onReady() {
        if (out != null) {
            out.println("READY");
            ready.setDisable(true);
            Label status = new Label("Ожидание других игроков...");
            counter.getChildren().add(status);
        }
    }

    @FXML
    void onStop() {
        if (out != null && gameActive) {
            out.println("STOP");
        }
        resetGame();
    }

    @FXML
    void onShot() {
        onArcherClicked();
    }

    public void shutdown() {
        isRun = false;
        if (threadForCircle != null) threadForCircle.interrupt();
        if (threadForArrow1 != null) threadForArrow1.interrupt();
        if (threadForArrow2 != null) threadForArrow2.interrupt();
        if (socket != null) {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}