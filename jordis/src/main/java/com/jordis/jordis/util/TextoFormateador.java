package com.jordis.jordis.util;

public final class TextoFormateador {

    private TextoFormateador() { }

    public static String humanizar(String valor) {
        if (valor == null || valor.isBlank()) return "";
        String minuscula = valor.toLowerCase().replace('_', ' ').trim();
        return Character.toUpperCase(minuscula.charAt(0)) + minuscula.substring(1);
    }

    public static String etiquetaAccion(String accion) {
        if (accion == null) return "";
        return switch (accion) {
            case "VENTA_REGISTRADA"       -> "Venta registrada";
            case "VENTA_ANULADA"          -> "Venta anulada";
            case "DEVOLUCION_REGISTRADA"  -> "Devolución registrada";
            case "COMPRA_REGISTRADA"      -> "Compra registrada";
            case "CLIENTE_CREADO"         -> "Cliente nuevo";
            case "PRODUCTO_CREADO"        -> "Producto nuevo";
            case "PROVEEDOR_CREADO"       -> "Proveedor nuevo";
            case "USUARIO_CREADO"         -> "Usuario nuevo";
            case "USUARIO_DESBLOQUEADO"   -> "Usuario desbloqueado";
            case "PRECIO_MODIFICADO"      -> "Precio modificado";
            case "INVENTARIO_AJUSTADO"    -> "Ajuste de inventario";
            case "CAJA_ABIERTA"           -> "Caja abierta";
            case "CIERRE_CAJA"            -> "Cierre de caja";
            default -> humanizar(accion);
        };
    }
}