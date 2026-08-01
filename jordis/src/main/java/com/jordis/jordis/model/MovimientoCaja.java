package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro genérico de movimientos de caja originados por operaciones
 * que no son una venta directa (por ahora: devoluciones). El "monto" es
 * negativo cuando sale efectivo real de la caja, y puede ser cero para
 * movimientos que no afectan el efectivo (cambio de producto, nota de
 * crédito, saldo a favor) pero que igual queremos trazar.
 * <p>
 * Diseñado para crecer: cualquier operación futura que afecte caja
 * (gastos, retiros, etc.) puede insertar aquí sin cambiar esta tabla.
 */
@Entity
@Table(name = "movimiento_caja")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoMovimientoCaja tipo;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "referencia_tipo", nullable = false, length = 30)
    private String referenciaTipo;

    @Column(name = "referencia_id", nullable = false)
    private Integer referenciaId;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
}