package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Compra;
import com.jordis.jordis.service.CompraService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
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
public class CompraController {

    @FXML private TableView<Compra> tablaCompras;
    @FXML private TableColumn<Compra, String> colId;
    @FXML private TableColumn<Compra, String> colFecha;
    @FXML private TableColumn<Compra, String> colProveedor;
    @FXML private TableColumn<Compra, String> colProductos;
    @FXML private TableColumn<Compra, String> colTotal;
    @FXML private TableColumn<Compra, String> colEstado;
    @FXML private TableColumn<Compra, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final CompraService compraService;
    private final SpringFXMLLoader fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarCompras();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdCompra())));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaPedido().format(FMT)));
        colProveedor.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getProveedor().getNombre()));

        colProductos.setCellValueFactory(d -> {
            String resumen = d.getValue().getDetalles().stream()
                    .map(det -> det.getProducto().getNombre() + " x" + det.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen.isEmpty() ? "—" : resumen);
        });

        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().getTotalCompra().toPlainString()));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                String estado = ((Compra) getTableRow().getItem()).getEstado();
                String fondo = switch (estado) {
                    case "PENDIENTE" -> "#FEF3C7";
                    case "RECIBIDA"  -> "#DCFCE7";
                    default          -> "#FEE2E2";
                };
                String color = switch (estado) {
                    case "PENDIENTE" -> "#B45309";
                    case "RECIBIDA"  -> "#15803D";
                    default          -> "#DC2626";
                };
                Label badge = new Label(estado);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnRecibir  = btn("Recibir",  "#15803D", "#DCFCE7");
            private final Button btnCancelar = btn("Cancelar", "#DC2626", "#FEE2E2");
            private final HBox box = new HBox(6, btnRecibir, btnCancelar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Compra c = (Compra) getTableRow().getItem();
                boolean pendiente = "PENDIENTE".equals(c.getEstado());
                btnRecibir.setOnAction(e -> recibirCompra(c));
                btnCancelar.setOnAction(e -> cancelarCompra(c));
                btnRecibir.setVisible(pendiente);
                btnRecibir.setManaged(pendiente);
                btnCancelar.setVisible(pendiente);
                btnCancelar.setManaged(pendiente);
                setGraphic(box);
            }
        });
    }

    private Button btn(String texto, String colorTexto, String colorFondo) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + colorFondo + "; -fx-text-fill: "
                + colorTexto + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 8; -fx-cursor: hand;");
        return b;
    }

    private void cargarCompras() {
        tablaCompras.setItems(
                FXCollections.observableArrayList(compraService.obtenerTodas()));
        lblMensaje.setText("");
    }

    @FXML public void onVerPendientes() {
        tablaCompras.setItems(
                FXCollections.observableArrayList(compraService.obtenerPendientes()));
        mostrarMensaje("Mostrando solo compras pendientes.", false);
    }

    @FXML public void onVerTodas() { cargarCompras(); }

    @FXML
    public void onNuevaCompra() {
        try {
            SpringFXMLLoader.LoadResult<CompraFormController> result =
                    fxmlLoader.loadWithController("/fxml/compra_form.fxml");

            // Limpiar el formulario antes de mostrar (fix singleton)
            result.controller.prepararNuevaCompra();

            result.controller.setOnGuardado(() -> {
                cargarCompras();
                mostrarMensaje("Compra registrada correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Nueva Compra");
            stage.setScene(new Scene(result.root, 700, 600));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de compra", e);
            mostrarMensaje("Error al abrir el formulario.", true);
        }
    }

    private void recibirCompra(Compra compra) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Marcar la compra #" + compra.getIdCompra()
                        + " como recibida? El stock se actualizará automáticamente.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar recepción");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                try {
                    compraService.recibirCompra(compra.getIdCompra());
                    cargarCompras();
                    mostrarMensaje("Compra recibida. Stock actualizado.", false);
                } catch (Exception e) {
                    mostrarMensaje("Error: " + e.getMessage(), true);
                }
            }
        });
    }

    private void cancelarCompra(Compra compra) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Cancelar la compra #" + compra.getIdCompra() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar cancelación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                compraService.cancelarCompra(compra.getIdCompra());
                cargarCompras();
                mostrarMensaje("Compra cancelada.", false);
            }
        });
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}