package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Devolución de uno o varios productos de una venta ya facturada.
 * NUNCA modifica la venta original: solo la referencia, resta del
 * inventario disponible que se puede devolver y registra su propio
 * historial (stock, caja, auditoría).
 */
@Entity
@Table(name = "devolucion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion")
    private Integer idDevolucion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_devolucion", nullable = false, length = 30)
    private TipoDevolucion tipoDevolucion;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoDevolucion estado = EstadoDevolucion.REGISTRADA;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "requiere_ncf", nullable = false)
    private Boolean requiereNcf = false;

    @Column(name = "ncf_nota_credito", length = 19)
    private String ncfNotaCredito;

    @Column(name = "incluye_itbis", nullable = false)
    private Boolean incluyeItbis = true;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DevolucionDetalle> detalles = new ArrayList<>();
}