package com.jordis.jordis.config;

import com.jordis.jordis.model.Usuario;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RolConverter implements AttributeConverter<Usuario.Rol, String> {

    @Override
    public String convertToDatabaseColumn(Usuario.Rol rol) {
        return rol == null ? null : rol.name();
    }

    @Override
    public Usuario.Rol convertToEntityAttribute(String valor) {
        return valor == null ? null : Usuario.Rol.valueOf(valor);
    }
}