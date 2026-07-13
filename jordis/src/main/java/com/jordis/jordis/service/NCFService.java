package com.jordis.jordis.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NCFService {

    private final JdbcTemplate jdbcTemplate;

    public NCFService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Tipos de NCF en RD:
     * B01 — Crédito Fiscal (empresas registradas en DGII)
     * B02 — Consumidor Final (persona natural sin RNC)
     * B14 — Régimen Especial de Tributación
     * B15 — Gubernamental
     */
    public String generarNCF(String tipoNcf) {
        String secuencia = switch (tipoNcf) {
            case "B01" -> "ncf_b01_seq";
            case "B02" -> "ncf_b02_seq";
            case "B14" -> "ncf_b14_seq";
            case "B15" -> "ncf_b15_seq";
            default -> throw new IllegalArgumentException(
                    "Tipo de NCF no reconocido: " + tipoNcf);
        };

        try {
            Long numero = jdbcTemplate.queryForObject(
                    "SELECT nextval('" + secuencia + "')", Long.class);
            if (numero == null) {
                throw new IllegalStateException(
                        "La secuencia '" + secuencia + "' no devolvió un valor.");
            }
            // Formato RD: B0100000001 (11 caracteres)
            return tipoNcf + String.format("%08d", numero);
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "No se pudo generar el NCF de tipo " + tipoNcf
                            + ". Verifica que la secuencia '" + secuencia
                            + "' exista en la base de datos.", e);
        }
    }

    public String[] obtenerTiposDisponibles() {
        return new String[]{
                "B01 — Crédito Fiscal",
                "B02 — Consumidor Final",
                "B14 — Régimen Especial",
                "B15 — Gubernamental"
        };
    }

    public String extraerCodigo(String tipoConDescripcion) {
        return tipoConDescripcion.split(" ")[0];
    }
}