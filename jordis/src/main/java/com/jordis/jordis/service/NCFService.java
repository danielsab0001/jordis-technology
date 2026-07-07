package com.jordis.jordis.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
public class NCFService {

    private final DataSource dataSource;

    public NCFService(DataSource dataSource) {
        this.dataSource = dataSource;
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
            default    -> "ncf_b01_seq";
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(
                     "SELECT nextval('" + secuencia + "')")) {
            if (rs.next()) {
                long numero = rs.getLong(1);
                // Formato RD: B0100000001 (11 caracteres)
                return tipoNcf + String.format("%08d", numero);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error generando NCF: " + e.getMessage());
        }
        throw new RuntimeException("No se pudo generar el NCF.");
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
        // "B01 — Crédito Fiscal" → "B01"
        return tipoConDescripcion.split(" ")[0];
    }
}