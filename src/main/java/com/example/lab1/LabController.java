package com.example.lab1;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;

import java.util.concurrent.atomic.AtomicReference;

public class LabController {
    @FXML
    Circle circleBig;
    @FXML
    Circle circleSmall;
    @FXML
    AnchorPane pane;
    @FXML
    Line lineSmall;
    @FXML
    Line lineBig;
    @FXML
    Line arrow;
    @FXML
    Polyline archer;
    @FXML
    Pane counter;
    @FXML
    Label counterOfShot;
    @FXML
    Label counterOfPlayer;

    Thread threadForCircle;
    Thread threadForArrow;
    volatile boolean isRun;
    volatile boolean isPause;
    volatile boolean isFly;
    private final Object lockObject = new Object();

    private static final int STEP_BY_BIGCIRCLE = 5;
    private static final int STEP_BY_SMALLCIRCLE = STEP_BY_BIGCIRCLE * 2;
    private static int shot = 0;
    private static int shotPlayer = 0;
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

    @FXML
    public void initialize() {
        currentPointForCircleBig =
                new AtomicReference<>(new Point(circleBig.getLayoutX(), circleBig.getLayoutY()));
        currentPointForCircleSmall =
                new AtomicReference<>(new Point(circleSmall.getLayoutX(), circleSmall.getLayoutY()));
        currentPointForArrow =
                new AtomicReference<>(new Point(arrow.getEndX(), arrow.getEndY()));

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
                arrow.setEndX(pArrow.x);
                arrow.setEndY(pArrow.y);
                arrow.setStartX(pArrow.x - LENGTH_OF_ARROW);
                arrow.setStartY(pArrow.y);
            }
        };

        renderer.start();
    }

    void next() {
        currentPointForCircleBig.getAndUpdate(point -> {
            double ty = point.y;
            ty += STEP_BY_BIGCIRCLE;
            if (ty > lineBig.getEndY()) ty = lineBig.getStartY();
            return new Point(point.x, ty);
        });

        currentPointForCircleSmall.getAndUpdate(point -> {
            double ty = point.y;
            ty -= STEP_BY_SMALLCIRCLE;
            if (ty < lineBig.getStartY()) ty = lineBig.getEndY();
            return new Point(point.x, ty);
        });
    }

    @FXML
    void onStart() {
        if(threadForCircle != null) return;
        threadForCircle = new Thread(() -> {
            isRun = true;
            isPause = false;
            while (isRun) {
                next();
                synchronized(lockObject) {
                    if(isPause) {
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

    @FXML
    void onPause() {
        isPause = true;
    }

    @FXML
    void onContinue() {
        synchronized (lockObject) {
            isPause = false;
            lockObject.notifyAll();
        }
    }

    @FXML
    void onStop() {
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

        if (currentPointForArrow != null) {
            currentPointForArrow.set(new Point(-63.33331298828125, 1.52587890625E-5));
        }
    }

    void nextFlyStep() {
        currentPointForArrow.getAndUpdate(point -> {
            double tx = point.x;
            double ty = point.y;
            tx += STEP_BY_SMALLCIRCLE;
            // промах tx == верхнему левому углу окна
            if(point.x <= counter.getLayoutX() && tx >= counter.getLayoutX()) {
                isFly = false;
                threadForArrow.interrupt();
                threadForArrow = null;
                return new Point(-63.33331298828125, 1.52587890625E-5);
            }
            // попадание в большую и маленькую мишень
            if (tx >= circleBig.getCenterX() - circleBig.getRadius() && tx <= circleBig.getCenterX() + circleBig.getRadius()
            && ty <= circleBig.getCenterY() - circleBig.getRadius() && ty >= circleBig.getCenterY() + circleBig.getRadius()) {
                counterOfPlayer.setText(String.valueOf(shotPlayer + 1));
                isFly = false;
                threadForArrow.interrupt();
                threadForArrow = null;
                return new Point(-63.33331298828125, 1.52587890625E-5);
            } else if (tx >= circleSmall.getCenterX() - circleSmall.getRadius() && tx <= circleSmall.getCenterX() + circleSmall.getRadius()
                    && ty <= circleSmall.getCenterY() - circleSmall.getRadius() && ty >= circleSmall.getCenterY() + circleSmall.getRadius()) {
                counterOfPlayer.setText(String.valueOf(shotPlayer + 1));
                isFly = false;
                threadForArrow.interrupt();
                threadForArrow = null;
                return new Point(-63.33331298828125, 1.52587890625E-5);
            } else return new Point(tx, point.y);
        });
    }

    void arrowFlight() {
        if(threadForArrow != null) return;
        isFly = true;
        threadForArrow = new Thread(() -> {
            while(isFly) {
                nextFlyStep();
                synchronized(lockObject) {
                    if(isPause) {
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
        threadForArrow.start();
    }

    @FXML
    void onArcherClicked() {
        counterOfShot.setText(String.valueOf(shot + 1));
        arrowFlight();
    }
}
