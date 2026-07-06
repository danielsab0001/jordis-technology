package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.FacturaService;
import com.jordis.jordis.service.VentaService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final FacturaService   facturaService;
    private final SpringFXMLLoader fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_LARGO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
                new SimpleStringProperty(
                        d.getValue().getFechaHora().format(FMT_LARGO)));

        colCliente.setCellValueFactory(d -> {
            var c = d.getValue().getCliente();
            return new SimpleStringProperty(
                    c != null ? c.getNombreCompleto() : "—");
        });

        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getTotal().toPlainString()));

        colPagado.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getTotalPagado().toPlainString()));

        // Saldo con color
        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
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

        // Vencimiento con aviso visual
        colVencimiento.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                if (v.getFechaLimiteCredito() == null) {
                    setText("—"); setStyle(""); return;
                }
                boolean vencido = v.getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now()) && !v.estaCancelado();
                setText(vencido
                        ? "⚠ " + v.getFechaLimiteCredito().format(FMT)
                        : v.getFechaLimiteCredito().format(FMT));
                setStyle(vencido
                        ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "");
            }
        });

        // Estado con badge
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
                if (v.estaCancelado()) {
                    texto = "Pagado";
                    fondo = "#DCFCE7"; color = "#15803D";
                } else if (v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now())) {
                    texto = "Vencido";
                    fondo = "#FEE2E2"; color = "#DC2626";
                } else {
                    texto = "Pendiente";
                    fondo = "#FEF3C7"; color = "#B45309";
                }
                Label badge = new Label(texto);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        // Acciones
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnPagar   = crearBtn("Registrar pago",
                    "#15803D", "#DCFCE7");
            private final Button btnFactura = crearBtn("Ver factura",
                    "#2563EB", "#EFF6FF");
            private final HBox box = new HBox(5, btnPagar, btnFactura);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                btnPagar.setOnAction(e -> abrirFormPago(v));
                btnFactura.setOnAction(e -> verFactura(v));

                // Ocultar botón de pago si ya está cancelado
                btnPagar.setVisible(!v.estaCancelado());
                btnPagar.setManaged(!v.estaCancelado());

                setGraphic(box);
            }
        });
    }

    private Button crearBtn(String texto, String colorTexto, String colorFondo) {
        Button btn = new Button(texto);
        btn.setStyle(
                "-fx-background-color: " + colorFondo
                        + "; -fx-text-fill: " + colorTexto
                        + "; -fx-border-color: " + colorTexto
                        + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                        + " -fx-font-size: 10; -fx-padding: 4 8; -fx-cursor: hand;");
        return btn;
    }

    private void cargarCreditos() {
        tablaCreditos.setItems(
                FXCollections.observableArrayList(ventaService.obtenerCreditos()));
    }

    @FXML
    public void onVerPendientes() {
        List<Venta> pendientes = ventaService.obtenerCreditos().stream()
                .filter(v -> !v.estaCancelado())
                .toList();
        tablaCreditos.setItems(FXCollections.observableArrayList(pendientes));
        mostrarMensaje(pendientes.size() + " crédito(s) pendiente(s).", false);
    }

    @FXML
    public void onVerTodos() {
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
            stage.setScene(new Scene(result.root, 580, 460));
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de pago", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void verFactura(Venta venta) {
        try {
            String ruta = facturaService.generarFactura(venta);
            facturaService.abrirPDF(ruta);
        } catch (Exception e) {
            log.error("Error generando factura", e);
            mostrarMensaje("Error al generar factura: " + e.getMessage(), true);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}