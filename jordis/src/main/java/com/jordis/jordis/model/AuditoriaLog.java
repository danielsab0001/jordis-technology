package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Rastro de quién hizo qué en las acciones más sensibles del sistema
 * (anular una venta, cambiar un precio, desbloquear un usuario, etc.).
 * Solo se agrega, nunca se edita ni se borra — es un historial.
 */
@Entity
@Table(name = "auditoria_log")
@Data
@NoArgsConstructor
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Integer idAuditoria;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Column(name = "entidad", length = 50)
    private String entidad;

    @Column(name = "id_entidad")
    private Integer idEntidad;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;
}