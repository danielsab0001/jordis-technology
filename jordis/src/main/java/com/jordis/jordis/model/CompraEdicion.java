package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "compra_edicion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraEdicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_edicion")
    private Integer idEdicion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "motivo", nullable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "cambios", columnDefinition = "TEXT")
    private String cambios;

    @Column(name = "nota_credito", nullable = false)
    private Boolean notaCredito = false;
}