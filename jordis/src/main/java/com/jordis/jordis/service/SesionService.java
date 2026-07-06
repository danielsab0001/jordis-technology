package com.jordis.jordis.service;

import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@Slf4j
public class SesionService {

    private static final int MINUTOS_INACTIVIDAD = 15;

    private PauseTransition timer;
    private Runnable onTimeout;

    /**
     * Inicia el timer de inactividad.
     * onTimeout se ejecuta al vencer el tiempo.
     */
    public void iniciarTimer(Runnable onTimeout) {
        this.onTimeout = onTimeout;
        timer = new PauseTransition(
                Duration.minutes(MINUTOS_INACTIVIDAD));
        timer.setOnFinished(e -> {
            log.info("Sesión cerrada por inactividad ({} min)",
                    MINUTOS_INACTIVIDAD);
            if (this.onTimeout != null) this.onTimeout.run();
        });
        timer.play();
    }

    /** Reinicia el contador al detectar actividad. */
    public void reiniciarTimer() {
        if (timer != null) {
            timer.playFromStart();
        }
    }

    /** Detiene el timer (al cerrar sesión manualmente). */
    public void detenerTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Registra los listeners de actividad en una escena.
     * Cualquier movimiento de mouse o tecla reinicia el timer.
     */
    public void registrarActividad(Scene scene) {
        scene.setOnMouseMoved(e    -> reiniciarTimer());
        scene.setOnMouseClicked(e  -> reiniciarTimer());
        scene.setOnKeyPressed(e    -> reiniciarTimer());
        scene.setOnKeyReleased(e   -> reiniciarTimer());
    }
}