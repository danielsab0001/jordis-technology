package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "venta_garantia")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaGarantia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_garantia")
    private Integer idGarantia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "meses", nullable = false)
    private Integer meses = 0;

    @Column(name = "fecha_vence")
    private LocalDateTime fechaVence;
}