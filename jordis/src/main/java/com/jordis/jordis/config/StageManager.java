package com.jordis.jordis.config;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StageManager {

    private Stage primaryStage;
    private final SpringFXMLLoader fxmlLoader;

    public StageManager(SpringFXMLLoader fxmlLoader) {
        this.fxmlLoader = fxmlLoader;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void switchScene(String fxmlPath, String title) {
        try {
            Parent root = fxmlLoader.load(fxmlPath);
            Scene scene = new Scene(root);
            primaryStage.setTitle("Jordis Technology — " + title);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            throw new RuntimeException("Error cargando pantalla: " + fxmlPath, e);
        }
    }
}