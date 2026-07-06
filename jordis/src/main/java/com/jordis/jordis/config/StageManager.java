package com.jordis.jordis.config;

import com.jordis.jordis.service.SesionService;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class StageManager {

    private Stage primaryStage;
    private final SpringFXMLLoader fxmlLoader;
    private final SesionService    sesionService;

    public StageManager(SpringFXMLLoader fxmlLoader,
                        SesionService sesionService) {
        this.fxmlLoader   = fxmlLoader;
        this.sesionService = sesionService;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void switchScene(String fxmlPath, String title) {
        try {
            Parent root  = fxmlLoader.load(fxmlPath);
            Scene  scene = new Scene(root);
            primaryStage.setTitle("Jordis Technology — " + title);
            primaryStage.setScene(scene);
            primaryStage.show();

            // Registrar actividad en la nueva escena
            sesionService.registrarActividad(scene);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error cargando pantalla: " + fxmlPath, e);
        }
    }
}