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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditoController {

    @FXML private TableView<Venta> tablaCreditos;
    @FXML private TableColumn<Venta, String> colFactura;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colPagado;
    @FXML private TableColumn<Venta, String> colSaldo;
    @FXML private TableColumn<Venta, String> colVencimiento;
    @FXML private TableColumn<Venta, String> colEstado;
    @FXML private TableColumn<Venta, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final VentaService     ventaService;
    private final SpringFXMLLoader fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarCreditos();
    }

    private void configurarColumnas() {
        colFactura.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getNumeroFactura() != null
                                ? d.getValue().getNumeroFactura() : "—"));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colCliente.setCellValueFactory(d -> {
            var c = d.getValue().getCliente();
            return new SimpleStringProperty(c != null ? c.getNombreCompleto() : "—");
        });
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getTotal().toPlainString()));
        colPagado.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getTotalPagado().toPlainString()));

        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                BigDecimal saldo = v.getSaldoPendiente();
                setText("RD$" + saldo.toPlainString());
                setStyle(saldo.compareTo(BigDecimal.ZERO) > 0
                        ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                        : "-fx-text-fill: #15803D; -fx-font-weight: bold;");
            }
        });

        colVencimiento.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                if (v.getFechaLimiteCredito() == null) {
                    setText("—"); setStyle(""); return;
                }
                String fecha = v.getFechaLimiteCredito().format(FMT);
                boolean vencido = v.getFechaLimiteCredito()
                        .isBefore(java.time.LocalDateTime.now())
                        && !v.estaCancelado();
                setText(vencido ? "⚠ " + fecha : fecha);
                setStyle(vencido ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "");
            }
        });

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                String texto, fondo, color;
                if (v.estaCancelado()) {
                    texto = "Pagado"; fondo = "#DCFCE7"; color = "#15803D";
                } else if (v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito()
                        .isBefore(java.time.LocalDateTime.now())) {
                    texto = "Vencido"; fondo = "#FEE2E2"; color = "#DC2626";
                } else {
                    texto = "Pendiente"; fondo = "#FEF3C7"; color = "#B45309";
                }
                Label badge = new Label(texto);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnPagar = new Button("Registrar pago");
            private final Button btnFactura = new Button("Factura");
            {
                btnPagar.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                        + " -fx-border-color: #BBF7D0; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 3 7;");
                btnFactura.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 3 7;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                btnPagar.setOnAction(e -> abrirFormPago(v));
                btnFactura.setOnAction(e -> regenerarFactura(v));
                btnPagar.setVisible(!v.estaCancelado());
                btnPagar.setManaged(!v.estaCancelado());
                var box = new javafx.scene.layout.HBox(5, btnPagar, btnFactura);
                setGraphic(box);
            }
        });
    }

    private void cargarCreditos() {
        tablaCreditos.setItems(
                FXCollections.observableArrayList(ventaService.obtenerCreditos()));
    }

    @FXML public void onVerPendientes() {
        List<Venta> pendientes = ventaService.obtenerCreditos().stream()
                .filter(v -> !v.estaCancelado())
                .toList();
        tablaCreditos.setItems(FXCollections.observableArrayList(pendientes));
        mostrarMensaje(pendientes.size() + " crédito(s) pendiente(s).", false);
    }

    @FXML public void onVerTodos() {
        cargarCreditos();
        lblMensaje.setText("");
    }

    private void abrirFormPago(Venta venta) {
        try {
            SpringFXMLLoader.LoadResult<CreditoPagoFormController> result =
                    fxmlLoader.loadWithController("/fxml/credito_pago_form.fxml");
            result.controller.setVenta(venta);
            result.controller.setOnGuardado(() -> {
                cargarCreditos();
                mostrarMensaje("Pago registrado correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Registrar pago — " + venta.getNumeroFactura());
            stage.setScene(new Scene(result.root, 560, 460));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de pago", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void regenerarFactura(Venta venta) {
        // Implementar con FacturaService si se necesita
        mostrarMensaje("Función de reimpresión de factura próximamente.", false);
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}