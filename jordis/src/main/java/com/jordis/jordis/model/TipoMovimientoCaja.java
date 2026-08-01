package com.jordis.jordis.model;

/**
 * Tipos de movimiento que puede registrar una devolución en la caja.
 * "conAfectacionEfectivo" indica si mueve dinero físico de la caja
 * (para el cálculo de efectivo esperado en el cierre de caja).
 */
public enum TipoMovimientoCaja {
    EGRESO_DEVOLUCION_EFECTIVO(true),
    SALDO_A_FAVOR(false),
    NOTA_CREDITO(false);

    private final boolean conAfectacionEfectivo;

    TipoMovimientoCaja(boolean conAfectacionEfectivo) {
        this.conAfectacionEfectivo = conAfectacionEfectivo;
    }

    public boolean afectaEfectivo() {
        return conAfectacionEfectivo;
    }
}