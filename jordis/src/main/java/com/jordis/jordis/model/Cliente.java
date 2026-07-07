package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "tipo_cliente", nullable = false, length = 10)
    private String tipoCliente = "PERSONA"; // PERSONA o EMPRESA

    // Campos comunes
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", length = 100)
    private String apellido; // null para empresas

    @Column(name = "cedula_identificacion", unique = true, length = 20)
    private String cedulaIdentificacion; // null para empresas

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "direccion", length = 255)
    private String direccion;

    // Campos exclusivos de empresa
    @Column(name = "rnc", unique = true, length = 20)
    private String rnc;

    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(name = "contacto_principal", length = 100)
    private String contactoPrincipal;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean esEmpresa() {
        return "EMPRESA".equals(tipoCliente);
    }

    public String getNombreCompleto() {
        if (esEmpresa()) return razonSocial != null ? razonSocial : nombre;
        return nombre + (apellido != null ? " " + apellido : "");
    }

    public String getIdentificador() {
        if (esEmpresa()) return "RNC: " + (rnc != null ? rnc : "—");
        return "Cédula: " + (cedulaIdentificacion != null ? cedulaIdentificacion : "—");
    }
}