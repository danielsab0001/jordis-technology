package com.jordis.jordis.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Aplica a un TextField un filtro que solo deja escribir números
 * decimales positivos (hasta 2 decimales), y formatea el valor a
 * "0.00" cuando el campo pierde el foco.
 */
public final class CampoDecimalUtil {

    private CampoDecimalUtil() {}

    private static final Pattern PATRON_PARCIAL =
            Pattern.compile("^\\d{0,10}(\\.\\d{0,2})?$");

    public static void aplicarFormatoMonetario(TextField campo) {
        UnaryOperator<TextFormatter.Change> filtro = change -> {
            String texto = change.getControlNewText();
            if (texto.isEmpty() || PATRON_PARCIAL.matcher(texto).matches()) {
                return change;
            }
            return null;
        };
        campo.setTextFormatter(new TextFormatter<>(filtro));

        campo.focusedProperty().addListener((obs, teniaFoco, tieneFoco) -> {
            if (!tieneFoco) {
                campo.setText(formatear(obtenerValor(campo)));
            }
        });
    }

    public static BigDecimal obtenerValor(TextField campo) {
        String texto = campo.getText();
        if (texto == null || texto.isBlank() || texto.equals(".")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static String formatear(BigDecimal valor) {
        return (valor != null ? valor : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}