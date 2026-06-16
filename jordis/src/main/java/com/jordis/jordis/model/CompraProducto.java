package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "compra_producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraProducto {

    @EmbeddedId
    private CompraProductoId id = new CompraProductoId();

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("idCompra")
    @JoinColumn(name = "id_compra")
    private Compra compra;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("idProducto")
    @JoinColumn(name = "id_producto")
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompraProductoId implements java.io.Serializable {
        private Integer idCompra;
        private Integer idProducto;
    }
}