package com.jordis.jordis.repository;

import com.jordis.jordis.model.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {

    @Query("""
            SELECT m
            FROM MovimientoCaja m
            WHERE m.fechaHora BETWEEN :desde AND :hasta
            ORDER BY m.fechaHora DESC
            """)
    List<MovimientoCaja> findEntreFechas(LocalDateTime desde, LocalDateTime hasta);

    /**
     * Suma de egresos en efectivo (montos negativos) generados por
     * devoluciones dentro del período de una caja abierta. Se usa para
     * restar del efectivo esperado en el cierre de caja.
     */
    @Query("""
            SELECT COALESCE(SUM(m.monto), 0)
            FROM MovimientoCaja m
            WHERE m.tipo = com.jordis.jordis.model.TipoMovimientoCaja.EGRESO_DEVOLUCION_EFECTIVO
            AND m.fechaHora BETWEEN :desde AND :hasta
            """)
    BigDecimal sumaEgresosDevolucionEfectivo(LocalDateTime desde, LocalDateTime hasta);
}