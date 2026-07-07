package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.CuentaPorPagar;
import com.jordis.jordis.service.CuentaPorPagarService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class CuentaPorPagarController {

    @FXML private TableView<CuentaPorPagar> tablaCuentas;
    @FXML private TableColumn<CuentaPorPagar, String> colId;
    @FXML private TableColumn<CuentaPorPagar, String> colProveedor;
    @FXML private TableColumn<CuentaPorPagar, String> colCompra;
    @FXML private TableColumn<CuentaPorPagar, String> colTotal;
    @FXML private TableColumn<CuentaPorPagar, String> colPagado;
    @FXML private TableColumn<CuentaPorPagar, String> colSaldo;
    @FXML private TableColumn<CuentaPorPagar, String> colVence;
    @FXML private TableColumn<CuentaPorPagar, String> colEstado;
    @FXML private TableColumn<CuentaPorPagar, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final CuentaPorPagarService cuentaService;
    private final SpringFXMLLoader      fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarCuentas();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdCuenta())));
        colProveedor.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getProveedor().getNombre()));
        colCompra.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "#" + d.getValue().getCompra().getIdCompra()));
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getMontoTotal().toPlainString()));
        colPagado.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getTotalPagado().toPlainString()));

        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                CuentaPorPagar c = (CuentaPorPagar) getTableRow().getItem();
                BigDecimal saldo = c.getSaldoPendiente();
                setText("RD$" + saldo.toPlainString());
                setStyle(saldo.compareTo(BigDecimal.ZERO) > 0
                        ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;"
                        : "-fx-text-fill: #15803D; -fx-font-weight: bold;");
            }
        });

        colVence.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                CuentaPorPagar c = (CuentaPorPagar) getTableRow().getItem();
                if (c.getFechaLimite() == null) {
                    setText("Sin límite"); setStyle(""); return;
                }
                boolean vencida = c.getFechaLimite().isBefore(LocalDateTime.now())
                        && !c.estaCancelada();
                setText(vencida
                        ? "⚠ " + c.getFechaLimite().format(FMT)
                        : c.getFechaLimite().format(FMT));
                setStyle(vencida
                        ? "-fx-text-fill: #DC2626; -fx-font-weight: bold;" : "");
            }
        });

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                CuentaPorPagar c = (CuentaPorPagar) getTableRow().getItem();
                boolean pagada = c.estaCancelada();
                boolean vencida = !pagada && c.getFechaLimite() != null
                        && c.getFechaLimite().isBefore(LocalDateTime.now());

                String texto = pagada ? "Pagada"
                        : vencida ? "Vencida" : "Pendiente";
                String fondo = pagada ? "#DCFCE7"
                        : vencida ? "#FEE2E2" : "#FEF3C7";
                String color = pagada ? "#15803D"
                        : vencida ? "#DC2626" : "#B45309";

                Label badge = new Label(texto);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnPagar = new Button("Pagar");
            {
                btnPagar.setStyle(
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                                + " -fx-border-color: #BBF7D0; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10;"
                                + " -fx-padding: 3 8; -fx-cursor: hand;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                CuentaPorPagar c = (CuentaPorPagar) getTableRow().getItem();
                if (c.estaCancelada()) { setGraphic(null); return; }
                btnPagar.setOnAction(e -> abrirFormPago(c));
                setGraphic(btnPagar);
            }
        });
    }

    private void cargarCuentas() {
        tablaCuentas.setItems(
                FXCollections.observableArrayList(cuentaService.obtenerTodas()));
    }

    @FXML public void onVerPendientes() {
        tablaCuentas.setItems(
                FXCollections.observableArrayList(cuentaService.obtenerPendientes()));
        mostrarMensaje("Mostrando solo cuentas pendientes.", false);
    }

    @FXML public void onVerTodas() {
        cargarCuentas();
        lblMensaje.setText("");
    }

    private void abrirFormPago(CuentaPorPagar cuenta) {
        try {
            SpringFXMLLoader.LoadResult<CuentaPagoFormController> result =
                    fxmlLoader.loadWithController("/fxml/cuenta_pago_form.fxml");
            result.controller.setCuenta(cuenta);
            result.controller.setOnGuardado(() -> {
                cargarCuentas();
                mostrarMensaje("Pago registrado correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Pagar a " + cuenta.getProveedor().getNombre());
            stage.setScene(new Scene(result.root, 580, 430));
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de pago", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12;"
                + " -fx-text-fill: " + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}