package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "venta_producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaProducto {

    @EmbeddedId
    private VentaProductoId id = new VentaProductoId();

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("idVenta")
    @JoinColumn(name = "id_venta")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("idProducto")
    @JoinColumn(name = "id_producto")
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaProductoId implements java.io.Serializable {
        private Integer idVenta;
        private Integer idProducto;
    }
}