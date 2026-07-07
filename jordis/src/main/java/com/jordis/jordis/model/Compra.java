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
@Table(name = "compra")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compra")
    private Integer idCompra;

    @Column(name = "fecha_pedido", nullable = false)
    private LocalDateTime fechaPedido = LocalDateTime.now();

    @Column(name = "fecha_recepcion")
    private LocalDateTime fechaRecepcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "PENDIENTE"; // PENDIENTE, RECIBIDA, CANCELADA

    @Column(name = "total_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCompra = BigDecimal.ZERO;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CompraProducto> detalles = new ArrayList<>();

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;
}