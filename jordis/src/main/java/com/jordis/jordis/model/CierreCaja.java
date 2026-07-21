package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierre_caja")
@Data
@NoArgsConstructor
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre")
    private Integer idCierre;

    @Column(name = "nombre_caja", nullable = false, length = 100)
    private String nombreCaja;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cajero", nullable = false)
    private Usuario cajero;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;

    @Column(name = "total_ventas", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVentas;

    @Column(name = "numero_ventas", nullable = false)
    private Integer numeroVentas;

    @Column(name = "ticket_promedio", nullable = false, precision = 12, scale = 2)
    private BigDecimal ticketPromedio;

    @Column(name = "productos_vendidos", nullable = false)
    private Integer productosVendidos;

    @Column(name = "monto_efectivo", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoEfectivo;

    @Column(name = "monto_tarjeta", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTarjeta;

    @Column(name = "monto_transferencia", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTransferencia;

    @Column(name = "monto_credito", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoCredito;

    @Column(name = "fondo_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal fondoInicial;

    @Column(name = "pagos_creditos", nullable = false, precision = 12, scale = 2)
    private BigDecimal pagosCreditos;

    @Column(name = "pagos_proveedores", nullable = false, precision = 12, scale = 2)
    private BigDecimal pagosProveedores;

    @Column(name = "gastos", nullable = false, precision = 12, scale = 2)
    private BigDecimal gastos;

    @Column(name = "retiros", nullable = false, precision = 12, scale = 2)
    private BigDecimal retiros;

    @Column(name = "efectivo_esperado", nullable = false, precision = 12, scale = 2)
    private BigDecimal efectivoEsperado;

    @Column(name = "efectivo_contado", precision = 12, scale = 2)
    private BigDecimal efectivoContado;

    @Column(name = "diferencia", precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "estado", length = 20)
    private String estado;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "total_por_cobrar_pendiente", precision = 12, scale = 2)
    private BigDecimal totalPorCobrarPendiente;

    @Column(name = "total_por_pagar_pendiente", precision = 12, scale = 2)
    private BigDecimal totalPorPagarPendiente;
}