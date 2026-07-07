package com.jordis.jordis.controller;

import com.jordis.jordis.model.AlertaSistema;
import com.jordis.jordis.service.AlertaService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertaController {

    @FXML private TableView<AlertaSistema> tablaAlertas;
    @FXML private TableColumn<AlertaSistema, String> colTipo;
    @FXML private TableColumn<AlertaSistema, String> colTitulo;
    @FXML private TableColumn<AlertaSistema, String> colDescripcion;
    @FXML private TableColumn<AlertaSistema, String> colFecha;
    @FXML private TableColumn<AlertaSistema, String> colEstado;
    @FXML private TableColumn<AlertaSistema, Void>   colAcciones;
    @FXML private Label lblContador;
    @FXML private Label lblMensaje;

    private final AlertaService alertaService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarAlertas();
    }

    private void configurarColumnas() {
        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                AlertaSistema a = (AlertaSistema) getTableRow().getItem();
                String[] estilo = estiloTipo(a.getTipo());
                Label badge = new Label(etiquetaTipo(a.getTipo()));
                badge.setStyle("-fx-background-color: " + estilo[0]
                        + "; -fx-text-fill: " + estilo[1]
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colTitulo.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getTitulo()));
        colDescripcion.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDescripcion() != null ? d.getValue().getDescripcion() : "—"));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                boolean leida = ((AlertaSistema) getTableRow().getItem()).getLeida();
                Label badge = new Label(leida ? "Leída" : "Nueva");
                badge.setStyle("-fx-background-color: " + (leida ? "#F1F5F9" : "#DBEAFE")
                        + "; -fx-text-fill: " + (leida ? "#64748B" : "#1E40AF")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnLeer    = btn("Marcar leída", "#15803D", "#DCFCE7");
            private final Button btnEliminar = btn("Eliminar",    "#DC2626", "#FEE2E2");
            private final HBox box = new HBox(5, btnLeer, btnEliminar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                AlertaSistema a = (AlertaSistema) getTableRow().getItem();
                btnLeer.setVisible(!a.getLeida());
                btnLeer.setManaged(!a.getLeida());
                btnLeer.setOnAction(e -> {
                    alertaService.marcarLeida(a.getIdAlerta());
                    cargarAlertas();
                });
                btnEliminar.setOnAction(e -> {
                    alertaService.eliminar(a.getIdAlerta());
                    cargarAlertas();
                });
                setGraphic(box);
            }
        });

        // Fila con fondo diferente si no está leída
        tablaAlertas.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AlertaSistema item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (!item.getLeida()) {
                    setStyle("-fx-background-color: #FEFCE8;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private Button btn(String texto, String colorTexto, String colorFondo) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + colorFondo + "; -fx-text-fill: "
                + colorTexto + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 7; -fx-cursor: hand;");
        return b;
    }

    private void cargarAlertas() {
        tablaAlertas.setItems(
                FXCollections.observableArrayList(alertaService.obtenerTodas()));
        actualizarContador();
    }

    private void actualizarContador() {
        long noLeidas = alertaService.contarNoLeidas();
        if (noLeidas > 0) {
            lblContador.setText(String.valueOf(noLeidas));
            lblContador.setVisible(true);
        } else {
            lblContador.setVisible(false);
        }
    }

    @FXML
    public void onEscanear() {
        alertaService.escanearTodo(); // un solo método que hace todo
        cargarAlertas();
        mostrarMensaje("Escaneo completado. Alertas actualizadas.", false);
    }

    @FXML
    public void onMarcarTodasLeidas() {
        alertaService.marcarTodasLeidas();
        cargarAlertas();
        mostrarMensaje("Todas las alertas marcadas como leídas.", false);
    }

    @FXML
    public void onFiltrarNoLeidas() {
        tablaAlertas.setItems(
                FXCollections.observableArrayList(alertaService.obtenerNoLeidas()));
        actualizarContador();
    }

    @FXML
    public void onVerTodas() {
        cargarAlertas();
        lblMensaje.setText("");
    }

    private String etiquetaTipo(String tipo) {
        return switch (tipo) {
            case "STOCK_BAJO"            -> "Stock bajo";
            case "PRECIO_DIFERENCIA"     -> "Revisar precio";
            case "USUARIO_BLOQUEADO"     -> "Usuario bloqueado";
            case "PRECIO_COMPRA_INUSUAL" -> "Precio inusual";
            case "CREDITO_VENCIMIENTO"   -> "Crédito por vencer";
            case "CUENTA_POR_PAGAR" -> "Cuenta por pagar";
            default                      -> tipo;
        };
    }

    private String[] estiloTipo(String tipo) {
        return switch (tipo) {
            case "STOCK_BAJO"            -> new String[]{"#FEF3C7", "#B45309"};
            case "PRECIO_DIFERENCIA"     -> new String[]{"#EDE9FE", "#6D28D9"};
            case "USUARIO_BLOQUEADO"     -> new String[]{"#FEE2E2", "#DC2626"};
            case "PRECIO_COMPRA_INUSUAL" -> new String[]{"#FFEDD5", "#C2410C"};
            case "CREDITO_VENCIMIENTO"   -> new String[]{"#FEE2E2", "#B91C1C"};
            case "CUENTA_POR_PAGAR"      -> new String[]{"#FEE2E2", "#B91C1C"};
            default                      -> new String[]{"#F1F5F9", "#64748B"};
        };
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}