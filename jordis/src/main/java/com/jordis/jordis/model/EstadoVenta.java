package com.jordis.jordis.model;

/**
 * Estados posibles de una venta. Una venta nunca se elimina físicamente:
 * cuando se anula, cambia de estado pero el registro permanece.
 */
public enum EstadoVenta {
    VALIDA,
    ANULADA
}