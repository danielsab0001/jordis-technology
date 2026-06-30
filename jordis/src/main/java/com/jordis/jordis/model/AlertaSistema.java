package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerta_sistema")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertaSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer idAlerta;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo; // STOCK_BAJO, PRECIO_DIFERENCIA, USUARIO_BLOQUEADO, PRECIO_COMPRA_INUSUAL

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "leida", nullable = false)
    private Boolean leida = false;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "id_referencia")
    private Integer idReferencia; // ID del producto, usuario, etc. según el tipo
}