package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer idVenta;

    @Column(name = "numero_factura", length = 20)
    private String numeroFactura;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cajero", nullable = false)
    private Usuario cajero;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago;

    @Column(name = "descuento_porcentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPorcentual = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "es_credito", nullable = false)
    private Boolean esCredito = false;

    @Column(name = "fecha_limite_credito")
    private LocalDateTime fechaLimiteCredito;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "es_credito_fiscal", nullable = false)
    private Boolean esCreditoFiscal = false;

    @Column(name = "ncf", length = 19)
    private String ncf;

    @Column(name = "tipo_ncf", length = 10)
    private String tipoNcf; // B01, B02, B14, B15

    @Column(name = "itbis_porcentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal itbisPorcentual = BigDecimal.ZERO;

    @Column(name = "monto_itbis", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoItbis = BigDecimal.ZERO;

    @Column(name = "monto_saldo_favor_aplicado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoSaldoAfavorAplicado = BigDecimal.ZERO;

    @Deprecated
    @Column(name = "anulada", nullable = false)
    private Boolean anulada = false;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @Column(name = "ncf_nota_credito_anulacion", length = 19)
    private String ncfNotaCreditoAnulacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.VALIDA;

    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<VentaProducto> detalles = new ArrayList<>();

    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<VentaGarantia> garantias = new ArrayList<>();

    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CreditoPago> pagos = new ArrayList<>();

    // Calcula cuánto se ha pagado del crédito
    public BigDecimal getTotalPagado() {
        return pagos.stream()
                .map(CreditoPago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getSaldoPendiente() {
        return total.subtract(getTotalPagado());
    }

    public boolean estaCancelado() {
        return getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0;
    }

    public boolean estaAnulada() {
        return estado == EstadoVenta.ANULADA;
    }
}
