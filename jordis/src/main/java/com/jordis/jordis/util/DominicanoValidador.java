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
        return aplicarGrupos(limpia, 3, 3, 4);
    }


    public static String formatearTelefonoParcial(String entrada) {
        String digitos = soloDigitos(entrada);
        if (digitos.length() > 10) digitos = digitos.substring(0, 10);
        return aplicarGrupos(digitos, 3, 3, 4);
    }

    /** Formato progresivo de cédula: 402-0474910-5 (3-7-1, hasta 11 dígitos). */
    public static String formatearCedulaParcial(String entrada) {
        String digitos = soloDigitos(entrada);
        if (digitos.length() > 11) digitos = digitos.substring(0, 11);
        return aplicarGrupos(digitos, 3, 7, 1);
    }

    /**
     * Formato progresivo de RNC. Un RNC de empresa tiene 9 dígitos
     * (formato 3-5-1, ej. 130-12345-6); un RNC de persona física es en
     * realidad su cédula (formato 3-7-1, 11 dígitos) — por eso el
     * agrupamiento cambia solo si el usuario sigue escribiendo más de 9
     * dígitos.
     */
    public static String formatearRncParcial(String entrada) {
        String digitos = soloDigitos(entrada);
        if (digitos.length() > 11) digitos = digitos.substring(0, 11);
        if (digitos.length() > 9) {
            return aplicarGrupos(digitos, 3, 7, 1);
        }
        return aplicarGrupos(digitos, 3, 5, 1);
    }

    /** Inserta guiones entre grupos de dígitos según los tamaños dados. */
    private static String aplicarGrupos(String digitos, int... grupos) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < grupos.length && pos < digitos.length(); i++) {
            int fin = Math.min(pos + grupos[i], digitos.length());
            sb.append(digitos, pos, fin);
            pos = fin;
            if (pos < digitos.length() && i < grupos.length - 1) {
                sb.append('-');
            }
        }
        return sb.toString();
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