package com.jordis.jordis.repository;

import com.jordis.jordis.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    // =========================
    // CONSULTAS GENERALES
    // =========================

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findActivas();

    @Query("""
            SELECT v
            FROM Venta v
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findTodasOrdenadas();

    /**
     * Página real (LIMIT/OFFSET en la base de datos) para el listado del
     * módulo de Ventas, con búsqueda opcional por número de factura o
     * nombre/razón social del cliente. Esto es lo que evita cargar la
     * tabla completa cada vez que se abre el módulo o se escribe en el
     * buscador — a diferencia de obtenerTodasIncluyendoAnuladas(), que
     * trae todo y se sigue usando en flujos puntuales que sí necesitan
     * el conjunto completo (por ahora ninguno crítico en volumen).
     */
    @Query(value = """
            SELECT v FROM Venta v
            LEFT JOIN v.cliente c
            WHERE (:texto IS NULL OR :texto = ''
                OR LOWER(v.numeroFactura) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.razonSocial) LIKE LOWER(CONCAT('%', :texto, '%')))
            ORDER BY v.fechaHora DESC
            """,
            countQuery = """
            SELECT COUNT(v) FROM Venta v
            LEFT JOIN v.cliente c
            WHERE (:texto IS NULL OR :texto = ''
                OR LOWER(v.numeroFactura) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.razonSocial) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Venta> buscarPaginado(@Param("texto") String texto, Pageable pageable);

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            AND v.fechaHora BETWEEN :desde AND :hasta
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findEntreFechas(LocalDateTime desde,
                                LocalDateTime hasta);

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            AND v.esCredito = true
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findCreditos();

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            AND v.cliente.idCliente = :idCliente
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findByCliente(Integer idCliente);

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.numeroFactura = :numero
            """)
    Optional<Venta> findByNumeroFactura(String numero);

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            AND v.cajero.idUsuario = :idCajero
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findByCajero(Integer idCajero);

    // =========================
    // PRODUCTOS VENDIDOS
    // =========================

    @Query("""
            SELECT COALESCE(SUM(vp.cantidad), 0)
            FROM VentaProducto vp
            WHERE vp.producto.idProducto = :idProducto
            AND vp.venta.anulada = false
            AND vp.venta.fechaHora >= :desde
            """)
    Integer totalVendidoDesde(Integer idProducto,
                              LocalDateTime desde);

    // =========================
    // VENTAS POR RANGO
    // =========================

    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.anulada = false
            AND v.fechaHora >= :inicio
            AND v.fechaHora < :fin
            ORDER BY v.fechaHora DESC
            """)
    List<Venta> findVentasEntre(LocalDateTime inicio,
                                LocalDateTime fin);

    @Query("""
            SELECT COALESCE(SUM(v.total), 0)
            FROM Venta v
            WHERE v.anulada = false
            AND v.fechaHora >= :inicio
            AND v.fechaHora < :fin
            """)
    BigDecimal totalVentas(LocalDateTime inicio,
                           LocalDateTime fin);

    @Query("""
            SELECT COUNT(v)
            FROM Venta v
            WHERE v.anulada = false
            AND v.fechaHora >= :inicio
            AND v.fechaHora < :fin
            """)
    long countVentas(LocalDateTime inicio,
                     LocalDateTime fin);

    // =========================
    // AGRUPACIÓN POR DÍA
    // =========================

    @Query("""
            SELECT
                FUNCTION('DATE', v.fechaHora),
                COALESCE(SUM(v.total),0),
                COUNT(v)
            FROM Venta v
            WHERE v.anulada = false
            AND v.fechaHora >= :desde
            GROUP BY FUNCTION('DATE', v.fechaHora)
            ORDER BY FUNCTION('DATE', v.fechaHora)
            """)
    List<Object[]> ventasPorDia(LocalDateTime desde);

    // =========================
    // TOP PRODUCTOS
    // =========================

    @Query("""
            SELECT
                vp.producto,
                SUM(vp.cantidad),
                SUM(vp.subtotal)
            FROM VentaProducto vp
            WHERE vp.venta.anulada = false
            AND vp.venta.fechaHora >= :desde
            GROUP BY vp.producto
            ORDER BY SUM(vp.cantidad) DESC
            """)
    List<Object[]> topProductos(LocalDateTime desde);

}