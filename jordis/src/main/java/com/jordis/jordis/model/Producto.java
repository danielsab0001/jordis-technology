package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 5;

    // Nuevo: último precio al que se compró
    @Column(name = "ultimo_precio_compra", precision = 12, scale = 2)
    private BigDecimal ultimoPrecioCompra;

    // Nuevo: precio sugerido de venta (calculado)
    @Column(name = "precio_sugerido", precision = 12, scale = 2)
    private BigDecimal precioSugerido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @Column(name = "marca", length = 100)
    private String marca;

    @Column(name = "modelo", length = 100)
    private String modelo;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isStockBajo() {
        return stock <= stockMinimo;
    }

    // Calcula y actualiza el precio sugerido con el margen dado
    // Ej: margen 1.30 = 30% sobre el costo
    public void calcularPrecioSugerido(BigDecimal margen) {
        if (ultimoPrecioCompra != null && margen != null) {
            this.precioSugerido = ultimoPrecioCompra
                    .multiply(margen)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}