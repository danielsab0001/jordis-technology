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

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente; // puede ser null (venta sin cliente registrado)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cajero", nullable = false)
    private Usuario cajero;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago; // EFECTIVO, TARJETA, TRANSFERENCIA

    @Column(name = "descuento_porcentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPorcentual = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "anulada", nullable = false)
    private Boolean anulada = false;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<VentaProducto> detalles = new ArrayList<>();
}