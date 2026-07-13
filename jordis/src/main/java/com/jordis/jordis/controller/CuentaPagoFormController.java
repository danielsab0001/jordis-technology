package com.jordis.jordis.controller;

import com.jordis.jordis.model.CuentaPorPagar;
import com.jordis.jordis.service.AlertaService;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.CuentaPorPagarService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class CuentaPagoFormController {

    @FXML private Label     lblResumen;
    @FXML private Label     lblSaldo;
    @FXML private TextField txtMonto;
    @FXML private ComboBox<String> cmbMetodoPago;
    @FXML private TextField txtNotas;
    @FXML private Label     lblError;
    @FXML private Button    btnGuardar;
    @FXML private DatePicker dpFechaPago;

    private final CuentaPorPagarService cuentaService;
    private final AutenticacionService  autenticacionService;
    private final AlertaService         alertaService;

    private CuentaPorPagar cuenta;
    private Runnable onGuardado;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        cmbMetodoPago.getItems().setAll(
                "EFECTIVO", "TARJETA", "TRANSFERENCIA",
                "CHEQUE");
        cmbMetodoPago.setValue("TRANSFERENCIA");
        dpFechaPago.setValue(java.time.LocalDate.now());
    }

    public void setCuenta(CuentaPorPagar c) {
        this.cuenta = c;
        lblResumen.setText(
                "Proveedor: " + c.getProveedor().getNombre()
                        + "  |  Compra #" + c.getCompra().getIdCompra()
                        + "  |  Total: RD$" + c.getMontoTotal().toPlainString()
                        + (c.getFechaLimite() != null
                        ? "  |  Vence: " + c.getFechaLimite().format(FMT) : ""));
        lblSaldo.setText(
                "Saldo pendiente: RD$" + c.getSaldoPendiente().toPlainString());
        txtMonto.setText(c.getSaldoPendiente().toPlainString());
        lblError.setText("");
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {
        lblError.setText("");
        if (txtMonto.getText().isBlank()) {
            lblError.setText("Ingresa el monto a pagar."); return;
        }

        BigDecimal monto;
        try {
            monto = new BigDecimal(txtMonto.getText().trim());
        } catch (NumberFormatException e) {
            lblError.setText("El monto debe ser un número válido."); return;
        }

        if (dpFechaPago.getValue() == null) {
            lblError.setText("Selecciona la fecha del pago."); return;
        }

        try {
            cuentaService.registrarPago(
                    cuenta.getIdCuenta(),
                    monto,
                    cmbMetodoPago.getValue(),
                    txtNotas.getText().trim(),
                    autenticacionService.getUsuarioActivo(),
                    dpFechaPago.getValue().atTime(java.time.LocalTime.now()));

            // Actualizar alertas tras el pago
            alertaService.escanearCuentasPorPagar();

            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}