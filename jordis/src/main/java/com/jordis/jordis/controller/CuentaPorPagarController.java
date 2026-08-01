package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.CuentaPorPagar;
import com.jordis.jordis.service.CuentaPorPagarService;
import com.jordis.jordis.util.Paginador;
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
    @FXML private TextField txtBuscar;

    private final CuentaPorPagarService cuentaService;
    private final SpringFXMLLoader      fxmlLoader;
    private Paginador<CuentaPorPagar> paginador;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        configurarColumnas();

        paginador = new Paginador<>(tablaCuentas);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaCuentas.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) ->
                filtrarCuentas(val));

        cargarCuentas();
    }

    private void filtrarCuentas(String texto) {
        List<CuentaPorPagar> base = cuentaService.obtenerTodas();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base); return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(c -> c.getProveedor().getNombre()
                        .toLowerCase().contains(t))
                .toList());
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
                        ? "" + c.getFechaLimite().format(FMT)
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
            private final Button btnPagar     = new Button("Pagar");
            private final Button btnHistorial = new Button("Ver pagos");
            private final HBox box = new HBox(5, btnPagar, btnHistorial);

            {
                btnPagar.setStyle(
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                                + " -fx-border-color: #BBF7D0; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10;"
                                + " -fx-padding: 3 8; -fx-cursor: hand;");
                btnHistorial.setStyle(
                        "-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9;"
                                + " -fx-border-color: #C4B5FD; -fx-border-radius: 4;"
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
                btnPagar.setOnAction(e -> abrirFormPago(c));
                btnHistorial.setOnAction(e -> verHistorialPagos(c));
                btnPagar.setVisible(!c.estaCancelada());
                btnPagar.setManaged(!c.estaCancelada());
                setGraphic(box);
            }
        });
    }

    private void verHistorialPagos(CuentaPorPagar cuenta) {
        try {
            SpringFXMLLoader.LoadResult<HistorialPagosController> result =
                    fxmlLoader.loadWithController("/fxml/historial_pagos.fxml");
            result.controller.setCuenta(cuenta);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Historial de pagos — "
                    + cuenta.getProveedor().getNombre());
            stage.setScene(new Scene(result.root, 700, 460));
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo historial", e);
            mostrarMensaje("Error: " + e.getMessage(), true);
        }
    }

    private void cargarCuentas() {
        paginador.setDatos(cuentaService.obtenerTodas());
    }

    // Filtra la tabla para mostrar únicamente la cuenta indicada.
    // Se usa al navegar aquí desde el Centro de Alertas.
    public void filtrarPorId(Integer idCuenta) {
        if (idCuenta == null) return;
        CuentaPorPagar c;
        try {
            c = cuentaService.obtenerPorId(idCuenta);
        } catch (Exception e) {
            mostrarMensaje("La cuenta de la alerta ya no existe.", true);
            return;
        }
        txtBuscar.setText(c.getProveedor().getNombre());
        paginador.setDatos(List.of(c));
        mostrarMensaje("Mostrando cuenta de: " + c.getProveedor().getNombre(), false);
    }

    @FXML
    public void onVerPendientes() {
        List<CuentaPorPagar> pendientes = cuentaService.obtenerTodas()
                .stream().filter(c -> !c.estaCancelada()).toList();
        paginador.setDatos(pendientes);
        mostrarMensaje(pendientes.size()
                + " cuenta(s) pendiente(s).", false);
    }

    @FXML
    public void onVerVencidas() {
        List<CuentaPorPagar> vencidas = cuentaService.obtenerTodas()
                .stream()
                .filter(c -> !c.estaCancelada()
                        && c.getFechaLimite() != null
                        && c.getFechaLimite().isBefore(LocalDateTime.now()))
                .toList();
        paginador.setDatos(vencidas);
        mostrarMensaje(vencidas.size() + " cuenta(s) vencida(s).", false);
    }

    @FXML
    public void onVerPorVencer() {
        LocalDateTime limite = LocalDateTime.now().plusDays(7);
        List<CuentaPorPagar> porVencer = cuentaService.obtenerTodas()
                .stream()
                .filter(c -> !c.estaCancelada()
                        && c.getFechaLimite() != null
                        && c.getFechaLimite().isBefore(limite))
                .toList();
        paginador.setDatos(porVencer);
        mostrarMensaje(porVencer.size()
                + " cuenta(s) por vencer en 7 días.", false);
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
            stage.setScene(new Scene(result.root, 580, 480));
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