package com.jordis.jordis.util;

import javafx.scene.control.TextField;

import java.util.function.UnaryOperator;

public final class CampoMascaraUtil {

    private CampoMascaraUtil() { }

    public static void aplicarMascaraTelefono(TextField campo) {
        aplicarMascara(campo, DominicanoValidador::formatearTelefonoParcial);
    }

    public static void aplicarMascaraCedula(TextField campo) {
        aplicarMascara(campo, DominicanoValidador::formatearCedulaParcial);
    }

    public static void aplicarMascaraRnc(TextField campo) {
        aplicarMascara(campo, DominicanoValidador::formatearRncParcial);
    }

    private static void aplicarMascara(TextField campo, UnaryOperator<String> formateador) {
        campo.textProperty().addListener((obs, valorAnterior, valorNuevo) -> {
            String formateado = formateador.apply(valorNuevo);
            if (!formateado.equals(valorNuevo)) {
                campo.setText(formateado);
                campo.positionCaret(formateado.length());
            }
        });
    }
}