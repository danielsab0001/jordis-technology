package com.jordis.jordis.controller;

import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.VentaService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditoPagoFormController {

    @FXML private Label     lblResumen;
    @FXML private Label     lblSaldo;
    @FXML private TextField txtMonto;
    @FXML private ComboBox<String> cmbMetodoPago;
    @FXML private TextField txtNotas;
    @FXML private Label     lblError;
    @FXML private Button    btnGuardar;
    @FXML private DatePicker dpFechaPago;

    private final VentaService         ventaService;
    private final AutenticacionService autenticacionService;


    private Venta    venta;
    private Runnable onGuardado;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        dpFechaPago.setValue(java.time.LocalDate.now());
        cmbMetodoPago.getItems().setAll(
                "EFECTIVO", "TARJETA", "TRANSFERENCIA", "CHEQUE");
        cmbMetodoPago.setValue("EFECTIVO");
    }

    public void setVenta(Venta v) {
        this.venta = v;
        lblResumen.setText("Factura: " + v.getNumeroFactura()
                + "  |  Cliente: "
                + (v.getCliente() != null ? v.getCliente().getNombreCompleto() : "—")
                + "  |  Total: RD$" + v.getTotal().toPlainString()
                + (v.getFechaLimiteCredito() != null
                ? "  |  Vence: " + v.getFechaLimiteCredito().format(FMT) : ""));
        lblSaldo.setText("Saldo pendiente: RD$" +
                v.getSaldoPendiente().toPlainString());
        // Sugerir el saldo total como monto por defecto
        txtMonto.setText(v.getSaldoPendiente().toPlainString());
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
            ventaService.registrarPagoCredito(
                    venta.getIdVenta(),
                    monto,
                    cmbMetodoPago.getValue(),
                    txtNotas.getText().trim(),
                    autenticacionService.getUsuarioActivo(),
                    dpFechaPago.getValue().atTime(java.time.LocalTime.now()));

            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (VentaService.VentaInvalidaException e) {
            lblError.setText(e.getMessage());
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}