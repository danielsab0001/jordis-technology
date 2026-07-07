package com.jordis.jordis.repository;

import com.jordis.jordis.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    @Query("SELECT v FROM Venta v WHERE v.anulada = false ORDER BY v.fechaHora DESC")
    List<Venta> findActivas();

    @Query("SELECT v FROM Venta v WHERE v.anulada = false AND " +
            "v.fechaHora BETWEEN :desde AND :hasta ORDER BY v.fechaHora DESC")
    List<Venta> findEntreFechas(LocalDateTime desde, LocalDateTime hasta);

    @Query("SELECT v FROM Venta v WHERE v.anulada = false AND v.esCredito = true " +
            "ORDER BY v.fechaHora DESC")
    List<Venta> findCreditos();

    @Query("SELECT v FROM Venta v WHERE v.anulada = false AND " +
            "v.cliente.idCliente = :idCliente ORDER BY v.fechaHora DESC")
    List<Venta> findByCliente(Integer idCliente);

    @Query("SELECT v FROM Venta v WHERE v.numeroFactura = :numero")
    Optional<Venta> findByNumeroFactura(String numero);

    @Query("SELECT COALESCE(SUM(vp.cantidad), 0) FROM VentaProducto vp " +
            "WHERE vp.producto.idProducto = :idProducto " +
            "AND vp.venta.anulada = false AND vp.venta.fechaHora >= :desde")
    Integer totalVendidoDesde(Integer idProducto, LocalDateTime desde);

    @Query("SELECT v FROM Venta v WHERE v.anulada = false AND " +
            "v.cajero.idUsuario = :idCajero ORDER BY v.fechaHora DESC")
    List<Venta> findByCajero(Integer idCajero);
}