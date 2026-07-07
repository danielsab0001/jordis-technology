package com.jordis.jordis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "configuracion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Configuracion {

    @Id
    @Column(name = "clave", length = 100)
    private String clave;

    @Column(name = "valor", nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}