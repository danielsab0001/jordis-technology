package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "contrasena", nullable = false, length = 255)
    private String contrasena;

    @Column(name = "rol", nullable = false)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado", nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sesion_activa", nullable = false)
    private Boolean sesionActiva = false;

    @Column(name = "sesion_iniciada_en")
    private LocalDateTime sesionIniciadaEn;

    @Column(name = "sesion_actualizada_en")
    private LocalDateTime sesionActualizadaEn;

    @Column(name = "sesion_maquina", length = 150)
    private String sesionMaquina;

    public enum Rol {
        ADMINISTRADOR, CAJERO
    }

    // Método de conveniencia
    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }
}