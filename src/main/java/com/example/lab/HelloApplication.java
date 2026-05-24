package com.example.lab;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    private LabController controller;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Lab.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setTitle("Lab3");
        stage.setScene(scene);
        controller = fxmlLoader.getController();
        stage.setOnCloseRequest(e -> {
            if (controller != null) {
                controller.shutdown();
            }
            DatabaseService.shutdown();
        });
        stage.show();
    }
}