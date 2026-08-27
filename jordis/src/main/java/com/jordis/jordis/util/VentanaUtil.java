package com.jordis.jordis.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class VentanaUtil {

    private VentanaUtil() {}

    /** Crea un Stage modal con un tamaño inicial dado, redimensionable libremente. */
    public static Stage crearDialogoModal(Parent root, String titulo,
                                          double ancho, double alto) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(titulo);
        stage.setResizable(true);
        stage.setScene(new Scene(root, ancho, alto));
        return stage;
    }

    /** Crea un Stage modal que toma el tamaño natural del contenido del FXML. */
    public static Stage crearDialogoModal(Parent root, String titulo) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(titulo);
        stage.setResizable(true);
        stage.setScene(new Scene(root));
        return stage;
    }
}