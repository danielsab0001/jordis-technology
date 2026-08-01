package com.jordis.jordis.controller;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.VentaService;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClienteHistorialController {

    @FXML private Label lblNombre;
    @FXML private Label lblIdentificador;
    @FXML private Label lblTotalCompras;
    @FXML private Label lblTotalGastado;
    @FXML private Label lblCreditoPendiente;
    @FXML private TableView<Venta> tablaVentas;
    @FXML private TableColumn<Venta, String> colFactura;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colProductos;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colPago;
    @FXML private TableColumn<Venta, String> colEstado;

    private final VentaService ventaService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        colFactura.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getNumeroFactura() != null
                                ? d.getValue().getNumeroFactura() : "—"));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colProductos.setCellValueFactory(d -> {
            String res = d.getValue().getDetalles().stream()
                    .map(vp -> vp.getProducto().getNombre()
                            + " x" + vp.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(res.isEmpty() ? "—" : res);
        });
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getTotal().toPlainString()));
        colPago.setCellValueFactory(d ->
                new SimpleStringProperty(
                        com.jordis.jordis.util.TextoFormateador.humanizar(d.getValue().getMetodoPago())));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                String texto, fondo, color;
                if (v.getAnulada()) {
                    texto = "Anulada"; fondo = "#FEE2E2"; color = "#DC2626";
                } else if (v.getEsCredito() && !v.estaCancelado()) {
                    texto = "Crédito pend."; fondo = "#FEF3C7"; color = "#B45309";
                } else {
                    texto = "Pagada"; fondo = "#DCFCE7"; color = "#15803D";
                }
                Label badge = new Label(texto);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 6; -fx-background-radius: 4;"
                        + " -fx-font-size: 10; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });
    }

    public void setCliente(Cliente cliente) {
        lblNombre.setText(cliente.getNombreCompleto());
        lblIdentificador.setText(cliente.getIdentificador());

        List<Venta> ventas = ventaService.filtrarPorCliente(
                cliente.getIdCliente());
        tablaVentas.setItems(FXCollections.observableArrayList(ventas));

        // Calcular resumen
        long totalCompras = ventas.stream()
                .filter(v -> !v.getAnulada()).count();
        BigDecimal totalGastado = ventas.stream()
                .filter(v -> !v.getAnulada())
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditoPendiente = ventas.stream()
                .filter(v -> !v.getAnulada() && v.getEsCredito())
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotalCompras.setText("Compras: " + totalCompras);
        lblTotalGastado.setText("Total gastado: RD$" + totalGastado.toPlainString());

        if (creditoPendiente.compareTo(BigDecimal.ZERO) > 0) {
            lblCreditoPendiente.setText(
                    "Crédito pendiente: RD$" + creditoPendiente.toPlainString());
            lblCreditoPendiente.setVisible(true);
        } else {
            lblCreditoPendiente.setVisible(false);
        }
    }

    @FXML
    public void onCerrar() {
        ((Stage) tablaVentas.getScene().getWindow()).close();
    }
}