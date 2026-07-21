package com.jordis.jordis.config;

import com.jordis.jordis.service.AutenticacionService;
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
    private final AutenticacionService autenticacionService;

    public StageManager(SpringFXMLLoader fxmlLoader,
                        SesionService sesionService,
                        AutenticacionService autenticacionService) {
        this.fxmlLoader   = fxmlLoader;
        this.sesionService = sesionService;
        this.autenticacionService = autenticacionService;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;

        this.primaryStage.setOnCloseRequest(e -> {
            if (autenticacionService.hayUsuarioActivo()) {
                autenticacionService.cerrarSesion();
            }
        });
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