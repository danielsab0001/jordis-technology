package com.jordis.jordis.controller;

import com.jordis.jordis.model.CreditoPago;
import com.jordis.jordis.model.CuentaPago;
import com.jordis.jordis.repository.CreditoPagoRepository;
import com.jordis.jordis.repository.CuentaPagoRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HistorialPagosController {

    @FXML private Label lblTitulo;
    @FXML private Label lblTotal;
    @FXML private Label lblTotalPagado;
    @FXML private Label lblSaldo;
    @FXML private TableView<FilaPago> tablaPagos;
    @FXML private TableColumn<FilaPago, String> colFecha;
    @FXML private TableColumn<FilaPago, String> colMonto;
    @FXML private TableColumn<FilaPago, String> colMetodo;
    @FXML private TableColumn<FilaPago, String> colRegistro;
    @FXML private TableColumn<FilaPago, String> colNotas;

    private final CreditoPagoRepository creditoPagoRepository;
    private final CuentaPagoRepository  cuentaPagoRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().fecha));
        colMonto.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().monto));
        colMetodo.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().metodo));
        colRegistro.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().registradoPor));
        colNotas.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().notas != null
                                ? d.getValue().notas : "—"));
    }

    // Para créditos de ventas (cuentas por cobrar)
    public void setVenta(com.jordis.jordis.model.Venta venta) {
        lblTitulo.setText("Factura: "
                + (venta.getNumeroFactura() != null
                ? venta.getNumeroFactura() : "#" + venta.getIdVenta())
                + "  |  Cliente: "
                + (venta.getCliente() != null
                ? venta.getCliente().getNombreCompleto() : "—"));
        lblTotal.setText("Total: RD$"
                + venta.getTotal().toPlainString());
        lblTotalPagado.setText("Pagado: RD$"
                + venta.getTotalPagado().toPlainString());
        lblSaldo.setText("Saldo: RD$"
                + venta.getSaldoPendiente().toPlainString());

        List<CreditoPago> pagos =
                creditoPagoRepository.findByVenta(venta.getIdVenta());
        List<FilaPago> filas = pagos.stream()
                .map(p -> new FilaPago(
                        p.getFechaPago().format(FMT),
                        p.getMonto().toPlainString(),
                        p.getMetodoPago(),
                        p.getCajero().getNombreCompleto(),
                        p.getNotas()))
                .toList();
        tablaPagos.setItems(FXCollections.observableArrayList(filas));
    }

    // Para cuentas por pagar a proveedores
    public void setCuenta(com.jordis.jordis.model.CuentaPorPagar cuenta) {
        lblTitulo.setText("Proveedor: "
                + cuenta.getProveedor().getNombre()
                + "  |  Compra #" + cuenta.getCompra().getIdCompra());
        lblTotal.setText("Total: RD$"
                + cuenta.getMontoTotal().toPlainString());
        lblTotalPagado.setText("Pagado: RD$"
                + cuenta.getTotalPagado().toPlainString());
        lblSaldo.setText("Saldo: RD$"
                + cuenta.getSaldoPendiente().toPlainString());

        List<CuentaPago> pagos =
                cuentaPagoRepository.findByCuenta(cuenta.getIdCuenta());
        List<FilaPago> filas = pagos.stream()
                .map(p -> new FilaPago(
                        p.getFechaPago().format(FMT),
                        p.getMonto().toPlainString(),
                        p.getMetodoPago(),
                        p.getCajero().getNombreCompleto(),
                        p.getNotas()))
                .toList();
        tablaPagos.setItems(FXCollections.observableArrayList(filas));
    }

    @FXML
    public void onCerrar() {
        ((Stage) tablaPagos.getScene().getWindow()).close();
    }

    // Record interno para unificar los dos tipos de pago en la misma tabla
    record FilaPago(String fecha, String monto,
                    String metodo, String registradoPor, String notas) {}
}