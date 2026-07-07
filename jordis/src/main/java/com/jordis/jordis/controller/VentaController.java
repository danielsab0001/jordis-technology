package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.VentaService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class VentaController {

    @FXML private TableView<Venta> tablaVentas;
    @FXML private TableColumn<Venta, String> colId;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colProductos;
    @FXML private TableColumn<Venta, String> colSubtotal;
    @FXML private TableColumn<Venta, String> colDescuento;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colPago;
    @FXML private TableColumn<Venta, String> colEstado;
    @FXML private TableColumn<Venta, Void>   colAnular;
    @FXML private Label lblMensaje;
    @FXML private TableColumn<Venta, String> colNcf;

    private final VentaService ventaService;
    private final SpringFXMLLoader fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarVentas();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdVenta())));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colCliente.setCellValueFactory(d -> {
            var cliente = d.getValue().getCliente();
            return new SimpleStringProperty(
                    cliente != null ? cliente.getNombreCompleto() : "Ocasional");
        });
        colProductos.setCellValueFactory(d -> {
            String resumen = d.getValue().getDetalles().stream()
                    .map(vp -> vp.getProducto().getNombre() + " x" + vp.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen.isEmpty() ? "—" : resumen);
        });
        colNcf.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); setText(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                if (v.getNcf() == null) {
                    setText("—"); setGraphic(null); return;
                }
                Label badge = new Label(v.getNcf());
                badge.setStyle(
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                                + " -fx-padding: 2 6; -fx-background-radius: 4;"
                                + " -fx-font-size: 10; -fx-font-weight: bold;");
                setGraphic(badge);
                setText(null);
            }
        });
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getSubtotal().toPlainString()));

        colDescuento.setCellValueFactory(d -> {
            var desc = d.getValue().getDescuentoPorcentual();
            return new SimpleStringProperty(
                    desc != null && desc.compareTo(java.math.BigDecimal.ZERO) > 0
                            ? desc.toPlainString() + "%" : "—");
        });
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getTotal().toPlainString()));
        colPago.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getMetodoPago()));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                boolean anulada = ((Venta) getTableRow().getItem()).getAnulada();
                Label badge = new Label(anulada ? "Anulada" : "Válida");
                badge.setStyle("-fx-background-color: " + (anulada ? "#FEE2E2" : "#DCFCE7")
                        + "; -fx-text-fill: " + (anulada ? "#DC2626" : "#15803D")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAnular.setCellFactory(col -> new TableCell<>() {
            private final Button btnAnular = new Button("Anular");
            {
                btnAnular.setStyle(
                        "-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;"
                                + " -fx-border-color: #FCA5A5; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 3 8;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                if (v.getAnulada()) { setGraphic(null); return; }
                btnAnular.setOnAction(e -> anularVenta(v));
                setGraphic(btnAnular);
            }
        });
    }

    private void cargarVentas() {
        tablaVentas.setItems(
                FXCollections.observableArrayList(ventaService.obtenerTodas()));
    }

    @FXML
    public void onNuevaVenta() {
        try {
            SpringFXMLLoader.LoadResult<VentaFormController> result =
                    fxmlLoader.loadWithController("/fxml/venta_form.fxml");

            // Limpiar el formulario antes de mostrar (fix singleton)
            result.controller.prepararNuevaVenta();

            result.controller.setOnGuardado(() -> {
                cargarVentas();
                mostrarMensaje("Venta registrada correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Nueva Venta");
            stage.setScene(new Scene(result.root, 750, 600));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de venta", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void anularVenta(Venta venta) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Anular venta");
        dialog.setHeaderText("Venta #" + venta.getIdVenta());
        dialog.setContentText("Motivo de anulación:");
        dialog.showAndWait().ifPresent(motivo -> {
            if (motivo.isBlank()) {
                mostrarMensaje("Debes ingresar un motivo.", true);
                return;
            }
            try {
                ventaService.anularVenta(venta.getIdVenta(), motivo);
                cargarVentas();
                mostrarMensaje("Venta anulada. Stock restaurado.", false);
            } catch (Exception e) {
                mostrarMensaje("Error: " + e.getMessage(), true);
            }
        });
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}