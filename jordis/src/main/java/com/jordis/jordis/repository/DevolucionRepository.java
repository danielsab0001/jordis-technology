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

    /**
     * Cantidad ya devuelta (en devoluciones activas) de un producto
     * específico dentro de una venta específica. Se usa para validar
     * que no se pueda devolver más de lo que quedó disponible.
     */
    @Query("""
            SELECT COALESCE(SUM(dd.cantidad), 0)
            FROM DevolucionDetalle dd
            WHERE dd.devolucion.venta.idVenta = :idVenta
            AND dd.producto.idProducto = :idProducto
            AND dd.devolucion.estado = com.jordis.jordis.model.EstadoDevolucion.REGISTRADA
            """)
    Integer cantidadYaDevuelta(Integer idVenta, Integer idProducto);
}