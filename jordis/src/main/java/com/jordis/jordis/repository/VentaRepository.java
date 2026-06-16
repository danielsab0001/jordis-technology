package com.jordis.jordis.repository;

import com.jordis.jordis.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    @Query("SELECT v FROM Venta v WHERE v.anulada = false ORDER BY v.fechaHora DESC")
    List<Venta> findActivas();

    @Query("SELECT v FROM Venta v WHERE v.anulada = false AND " +
            "v.fechaHora BETWEEN :desde AND :hasta ORDER BY v.fechaHora DESC")
    List<Venta> findEntreFechas(LocalDateTime desde, LocalDateTime hasta);

    // Para el algoritmo de recomendación: ventas de un producto en los últimos N días
    @Query("SELECT COALESCE(SUM(vp.cantidad), 0) FROM VentaProducto vp " +
            "WHERE vp.producto.idProducto = :idProducto " +
            "AND vp.venta.anulada = false " +
            "AND vp.venta.fechaHora >= :desde")
    Integer totalVendidoDesde(Integer idProducto, LocalDateTime desde);
}