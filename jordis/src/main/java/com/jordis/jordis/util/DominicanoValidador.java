package com.jordis.jordis.util;

import java.util.Set;
import java.util.regex.Pattern;

public final class DominicanoValidador {

    private DominicanoValidador() {}

    private static final Pattern NO_DIGITOS = Pattern.compile("\\D");
    private static final Set<String> CODIGOS_AREA_RD = Set.of("809", "829", "849");
    private static final Pattern EMAIL_BASICO =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static boolean esCedulaValida(String cedula) {
        String limpia = soloDigitos(cedula);
        if (limpia.length() != 11) return false;
        if (limpia.startsWith("000")) return false;

        int[] pesos = {1, 2, 1, 2, 1, 2, 1, 2, 1, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            int digito = Character.getNumericValue(limpia.charAt(i));
            int producto = digito * pesos[i];
            if (producto > 9) {
                producto = (producto / 10) + (producto % 10);
            }
            suma += producto;
        }
        int verificadorEsperado = (10 - (suma % 10)) % 10;
        int verificadorReal = Character.getNumericValue(limpia.charAt(10));
        return verificadorEsperado == verificadorReal;
    }

    public static boolean esRncValido(String rnc) {
        String limpia = soloDigitos(rnc);
        if (limpia.length() == 11) return esCedulaValida(limpia);
        return limpia.length() == 9;
    }

    public static boolean esTelefonoValido(String telefono) {
        String limpia = soloDigitos(telefono);
        if (limpia.length() != 10) return false;
        return CODIGOS_AREA_RD.contains(limpia.substring(0, 3));
    }

    public static String formatearTelefono(String telefono) {
        String limpia = soloDigitos(telefono);
        if (limpia.length() != 10) return telefono;
        return "(" + limpia.substring(0, 3) + ") "
                + limpia.substring(3, 6) + "-" + limpia.substring(6);
    }

    public static boolean esCorreoValido(String correo) {
        if (correo == null || correo.isBlank()) return false;
        return EMAIL_BASICO.matcher(correo.trim()).matches();
    }

    private static String soloDigitos(String valor) {
        if (valor == null) return "";
        return NO_DIGITOS.matcher(valor).replaceAll("");
    }
}