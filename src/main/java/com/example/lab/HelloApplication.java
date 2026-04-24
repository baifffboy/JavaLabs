package com.example.lab;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Lab.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setTitle("Lab2");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            LabController controller = fxmlLoader.getController();
            if (controller != null) {
                controller.shutdown();
            }
        });
        stage.show();
    }
}