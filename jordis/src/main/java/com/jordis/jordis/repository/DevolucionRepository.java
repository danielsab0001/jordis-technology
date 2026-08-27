package com.jordis.jordis.repository;

import com.jordis.jordis.model.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {

    @Query("""
            SELECT d
            FROM Devolucion d
            ORDER BY d.fechaHora DESC
            """)
    List<Devolucion> findTodasOrdenadas();

    @Query("""
            SELECT d
            FROM Devolucion d
            WHERE d.venta.idVenta = :idVenta
            AND d.estado = com.jordis.jordis.model.EstadoDevolucion.REGISTRADA
            ORDER BY d.fechaHora DESC
            """)
    List<Devolucion> findActivasPorVenta(Integer idVenta);

    @Query("""
            SELECT d
            FROM Devolucion d
            WHERE d.fechaHora BETWEEN :desde AND :hasta
            ORDER BY d.fechaHora DESC
            """)
    List<Devolucion> findEntreFechas(LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            SELECT COALESCE(SUM(dd.cantidad), 0)
            FROM DevolucionDetalle dd
            WHERE dd.detalleVenta.idDetalle = :idDetalleVenta
            AND dd.devolucion.estado = com.jordis.jordis.model.EstadoDevolucion.REGISTRADA
            """)
    Integer cantidadYaDevueltaPorDetalle(Integer idDetalleVenta);
}