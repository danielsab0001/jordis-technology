package com.jordis.jordis.controller;

import com.jordis.jordis.service.ConfiguracionService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfiguracionController {

    @FXML private TextField txtNombre;
    @FXML private TextField txtRnc;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtEmail;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtDiasCredito;
    @FXML private TextField txtDiasCuenta;
    @FXML private TextField txtMargen;
    @FXML private TextField txtPieFactura;
    @FXML private Label     lblMensaje;

    private final ConfiguracionService configuracionService;

    @FXML
    public void initialize() {
        txtNombre.setText(configuracionService.obtener(
                "negocio.nombre", "Jordis Technology"));
        txtRnc.setText(configuracionService.obtener(
                "negocio.rnc", ""));
        txtTelefono.setText(configuracionService.obtener(
                "negocio.telefono", ""));
        txtEmail.setText(configuracionService.obtener(
                "negocio.email", ""));
        txtDireccion.setText(configuracionService.obtener(
                "negocio.direccion", ""));
        txtDiasCredito.setText(configuracionService.obtener(
                "alerta.dias_credito", "7"));
        txtDiasCuenta.setText(configuracionService.obtener(
                "alerta.dias_cuenta", "7"));
        txtMargen.setText(configuracionService.obtener(
                "inventario.margen", "30"));
        txtPieFactura.setText(configuracionService.obtener(
                "factura.pie", "¡Gracias por su compra!"));
    }

    @FXML
    public void onGuardar() {
        // Validar numéricos
        try {
            Integer.parseInt(txtDiasCredito.getText().trim());
            Integer.parseInt(txtDiasCuenta.getText().trim());
            Integer.parseInt(txtMargen.getText().trim());
        } catch (NumberFormatException e) {
            mostrarMensaje("Los campos de días y margen deben ser números enteros.",
                    true);
            return;
        }

        if (txtNombre.getText().trim().isEmpty()) {
            mostrarMensaje("El nombre del negocio es obligatorio.", true);
            return;
        }

        configuracionService.guardar("negocio.nombre",
                txtNombre.getText().trim());
        configuracionService.guardar("negocio.rnc",
                txtRnc.getText().trim());
        configuracionService.guardar("negocio.telefono",
                txtTelefono.getText().trim());
        configuracionService.guardar("negocio.email",
                txtEmail.getText().trim());
        configuracionService.guardar("negocio.direccion",
                txtDireccion.getText().trim());
        configuracionService.guardar("alerta.dias_credito",
                txtDiasCredito.getText().trim());
        configuracionService.guardar("alerta.dias_cuenta",
                txtDiasCuenta.getText().trim());
        configuracionService.guardar("inventario.margen",
                txtMargen.getText().trim());
        configuracionService.guardar("factura.pie",
                txtPieFactura.getText().trim());

        mostrarMensaje("Configuración guardada correctamente.", false);
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}