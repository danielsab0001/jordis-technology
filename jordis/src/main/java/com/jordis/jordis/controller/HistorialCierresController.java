package com.jordis.jordis.controller;

import com.jordis.jordis.model.CierreCaja;
import com.jordis.jordis.service.CierreCajaService;
import com.jordis.jordis.service.FacturaService;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HistorialCierresController {

    @FXML private TextField txtBuscarCajero;
    @FXML private DatePicker dpFecha;
    @FXML private TableView<CierreCaja> tabla;
    @FXML private TableColumn<CierreCaja, String> colFecha;
    @FXML private TableColumn<CierreCaja, String> colApertura;
    @FXML private TableColumn<CierreCaja, String> colCierre;
    @FXML private TableColumn<CierreCaja, String> colCajero;
    @FXML private TableColumn<CierreCaja, String> colCaja;
    @FXML private TableColumn<CierreCaja, String> colTotal;
    @FXML private TableColumn<CierreCaja, String> colDiferencia;
    @FXML private TableColumn<CierreCaja, String> colEstado;
    @FXML private TableColumn<CierreCaja, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final CierreCajaService cierreCajaService;
    private final FacturaService facturaService;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA  = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFechaCierre().format(FMT_FECHA)));
        colApertura.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFechaApertura().format(FMT_HORA)));
        colCierre.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFechaCierre().format(FMT_HORA)));
        colCajero.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCajero() != null
                        ? d.getValue().getCajero().getNombreCompleto() : "—"));
        colCaja.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreCaja()));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(
                "RD$" + fmt(d.getValue().getTotalVentas())));
        colDiferencia.setCellValueFactory(d -> new SimpleStringProperty(
                (d.getValue().getDiferencia() != null
                        && d.getValue().getDiferencia().compareTo(BigDecimal.ZERO) > 0 ? "+" : "")
                        + "RD$" + fmt(d.getValue().getDiferencia())));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(
                etiquetaEstado(d.getValue().getEstado())));
        colEstado.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                CierreCaja c = getTableRow() != null
                        ? (CierreCaja) getTableRow().getItem() : null;
                String color = c == null ? "#64748B" : switch (c.getEstado()) {
                    case "EXACTO"   -> "#15803D";
                    case "SOBRANTE" -> "#B45309";
                    default          -> "#DC2626";
                };
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });

        colAcciones.setCellFactory(tc -> new TableCell<>() {
            private final Button btnPdf = new Button("📄  PDF");
            {
                btnPdf.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 5;"
                        + " -fx-background-radius: 5; -fx-font-size: 11;"
                        + " -fx-padding: 3 10; -fx-cursor: hand;");
                btnPdf.setOnAction(e -> generarPdf(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnPdf);
            }
        });

        dpFecha.valueProperty().addListener((obs, old, val) -> aplicarFiltros());
        txtBuscarCajero.textProperty().addListener((obs, old, val) -> aplicarFiltros());

        cargar();
    }

    private void cargar() {
        tabla.setItems(javafx.collections.FXCollections.observableArrayList(
                cierreCajaService.obtenerTodos()));
    }

    @FXML
    public void onActualizar() {
        dpFecha.setValue(null);
        txtBuscarCajero.clear();
        cargar();
        lblMensaje.setText("");
    }

    private void aplicarFiltros() {
        LocalDate fecha = dpFecha.getValue();
        String texto = txtBuscarCajero.getText() == null
                ? "" : txtBuscarCajero.getText().trim().toLowerCase();

        List<CierreCaja> filtrados = cierreCajaService.obtenerTodos().stream()
                .filter(c -> fecha == null
                        || c.getFechaCierre().toLocalDate().equals(fecha))
                .filter(c -> texto.isEmpty()
                        || (c.getCajero() != null && c.getCajero().getNombreCompleto()
                        .toLowerCase().contains(texto))
                        || c.getNombreCaja().toLowerCase().contains(texto))
                .toList();
        tabla.setItems(javafx.collections.FXCollections.observableArrayList(filtrados));
        lblMensaje.setText(filtrados.size() + " cierre(s) encontrado(s).");
    }

    private void generarPdf(CierreCaja c) {
        try {
            String ruta = cierreCajaService.generarPdf(c);
            facturaService.abrirPDF(ruta);
        } catch (Exception e) {
            log.error("Error generando PDF desde historial", e);
            lblMensaje.setText("Error generando el PDF: " + e.getMessage());
        }
    }

    private String etiquetaEstado(String estado) {
        if (estado == null) return "—";
        return switch (estado) {
            case "EXACTO"   -> "✔ Exacto";
            case "SOBRANTE" -> "▲ Sobrante";
            case "FALTANTE" -> "▼ Faltante";
            default          -> estado;
        };
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }
}