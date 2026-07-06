package com.jordis.jordis.controller;

import com.jordis.jordis.model.Compra;
import com.jordis.jordis.model.CompraEdicion;
import com.jordis.jordis.model.CompraProducto;
import com.jordis.jordis.repository.CompraEdicionRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class CompraDetalleController {

    @FXML private Label  lblResumen;
    @FXML private Label  lblEstado;
    @FXML private TableView<CompraProducto> tablaProductos;
    @FXML private TableColumn<CompraProducto, String> colProducto;
    @FXML private TableColumn<CompraProducto, String> colPedido;
    @FXML private TableColumn<CompraProducto, String> colRecibido;
    @FXML private TableColumn<CompraProducto, String> colDiferencia;
    @FXML private TableColumn<CompraProducto, String> colCosto;
    @FXML private TableColumn<CompraProducto, String> colSubtotal;
    @FXML private TableView<CompraEdicion>   tablaEdiciones;
    @FXML private TableColumn<CompraEdicion, String> colEdFecha;
    @FXML private TableColumn<CompraEdicion, String> colEdUsuario;
    @FXML private TableColumn<CompraEdicion, String> colEdMotivo;
    @FXML private TableColumn<CompraEdicion, String> colEdCambios;
    @FXML private TableColumn<CompraEdicion, String> colEdNota;

    private final CompraEdicionRepository edicionRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        // Columnas de productos
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getProducto().getNombre()));
        colPedido.setCellValueFactory(d -> {
            Integer pedida = d.getValue().getCantidadPedida();
            return new SimpleStringProperty(
                    pedida != null ? String.valueOf(pedida)
                            : String.valueOf(d.getValue().getCantidad()));
        });
        colRecibido.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getCantidad())));

        // Diferencia con color
        colDiferencia.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                CompraProducto cp = (CompraProducto) getTableRow().getItem();
                int pedida   = cp.getCantidadPedida() != null
                        ? cp.getCantidadPedida() : cp.getCantidad();
                int recibida = cp.getCantidad();
                int diff     = recibida - pedida;

                if (diff == 0) {
                    setText("✓");
                    setStyle("-fx-text-fill: #15803D; -fx-font-weight: bold;");
                } else {
                    setText(String.valueOf(diff));
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                }
            }
        });

        colCosto.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getCostoUnitario().toPlainString()));
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getSubtotal().toPlainString()));

        // Columnas de ediciones
        colEdFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colEdUsuario.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getUsuario().getNombreCompleto()));
        colEdMotivo.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getMotivo()));
        colEdCambios.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCambios() != null ? d.getValue().getCambios() : "—"));
        colEdNota.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getNotaCredito() ? "Sí" : "No"));
    }

    public void setCompra(Compra compra) {
        lblResumen.setText(
                "Compra #" + compra.getIdCompra()
                        + "  |  Proveedor: " + compra.getProveedor().getNombre()
                        + "  |  Fecha: " + compra.getFechaPedido().format(FMT)
                        + "  |  Total: RD$" + compra.getTotalCompra().toPlainString());

        String colorEstado = "RECIBIDA".equals(compra.getEstado())
                ? "#15803D" : "#B45309";
        lblEstado.setText("Estado: " + compra.getEstado());
        lblEstado.setStyle("-fx-font-size: 12; -fx-font-weight: bold; "
                + "-fx-text-fill: " + colorEstado + ";");

        tablaProductos.setItems(
                FXCollections.observableArrayList(compra.getDetalles()));
        tablaEdiciones.setItems(FXCollections.observableArrayList(
                edicionRepository.findByCompra(compra.getIdCompra())));
    }

    @FXML
    public void onCerrar() {
        ((Stage) tablaProductos.getScene().getWindow()).close();
    }
}