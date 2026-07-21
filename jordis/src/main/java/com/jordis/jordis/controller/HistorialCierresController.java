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
            private final Button btnVer = new Button("Ver detalle");
            private final Button btnPdf = new Button("PDF");
            private final HBox caja = new HBox(6, btnVer, btnPdf);
            {
                btnVer.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 5;"
                        + " -fx-background-radius: 5; -fx-font-size: 11;"
                        + " -fx-padding: 3 8; -fx-cursor: hand;");
                btnPdf.setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #374151;"
                        + " -fx-border-color: #E2E8F0; -fx-border-radius: 5;"
                        + " -fx-background-radius: 5; -fx-font-size: 11;"
                        + " -fx-padding: 3 8; -fx-cursor: hand;");
                btnVer.setOnAction(e -> mostrarDetalle(getTableView().getItems().get(getIndex())));
                btnPdf.setOnAction(e -> generarPdf(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : caja);
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

    private void mostrarDetalle(CierreCaja c) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Detalle del cierre — " + c.getNombreCaja());
        stage.setResizable(false);

        VBox root = new VBox(10);
        root.setStyle("-fx-background-color: white; -fx-padding: 24;");
        root.setPrefWidth(420);

        String colorEstado = switch (c.getEstado()) {
            case "EXACTO"   -> "#15803D";
            case "SOBRANTE" -> "#B45309";
            default          -> "#DC2626";
        };
        String fondoEstado = switch (c.getEstado()) {
            case "EXACTO"   -> "#F0FDF4";
            case "SOBRANTE" -> "#FFFBEB";
            default          -> "#FEF2F2";
        };

        Label titulo = new Label(c.getNombreCaja());
        titulo.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label sub = new Label("Cajero: " + (c.getCajero() != null ? c.getCajero().getNombreCompleto() : "—")
                + "\nApertura: " + c.getFechaApertura().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                + "\nCierre: " + c.getFechaCierre().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        sub.setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");

        Separator sep1 = new Separator();

        VBox resumen = new VBox(6);
        resumen.getChildren().addAll(
                filaDetalle("Total vendido", "RD$" + fmt(c.getTotalVentas())),
                filaDetalle("Número de ventas", String.valueOf(c.getNumeroVentas())),
                filaDetalle("Ticket promedio", "RD$" + fmt(c.getTicketPromedio())),
                filaDetalle("Productos vendidos", String.valueOf(c.getProductosVendidos())));

        Separator sep2 = new Separator();

        VBox metodos = new VBox(6);
        metodos.getChildren().addAll(
                filaDetalle("Efectivo", "RD$" + fmt(c.getMontoEfectivo())),
                filaDetalle("Tarjeta", "RD$" + fmt(c.getMontoTarjeta())),
                filaDetalle("Transferencia", "RD$" + fmt(c.getMontoTransferencia())),
                filaDetalle("Crédito", "RD$" + fmt(c.getMontoCredito())));

        Separator sep3 = new Separator();

        VBox efectivo = new VBox(6);
        efectivo.getChildren().addAll(
                filaDetalle("Fondo inicial", "RD$" + fmt(c.getFondoInicial())),
                filaDetalle("Gastos", "- RD$" + fmt(c.getGastos())),
                filaDetalle("Retiros", "- RD$" + fmt(c.getRetiros())),
                filaDetalle("Efectivo esperado", "RD$" + fmt(c.getEfectivoEsperado())),
                filaDetalle("Efectivo contado", "RD$" + fmt(c.getEfectivoContado())));

        Label resultado = new Label(etiquetaEstado(c.getEstado())
                + "  —  RD$" + fmt(c.getDiferencia() != null ? c.getDiferencia().abs() : BigDecimal.ZERO));
        resultado.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: " + colorEstado + ";"
                + " -fx-background-color: " + fondoEstado + "; -fx-background-radius: 8;"
                + " -fx-padding: 10; -fx-border-color: " + colorEstado + "; -fx-border-radius: 8;");
        resultado.setMaxWidth(Double.MAX_VALUE);
        resultado.setAlignment(javafx.geometry.Pos.CENTER);

        VBox raiz = new VBox(8, titulo, sub, sep1, resumen, sep2, metodos, sep3, efectivo, resultado);

        if (c.getObservacion() != null && !c.getObservacion().isBlank()) {
            Label lblObsTitulo = new Label("Observaciones");
            lblObsTitulo.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
            Label lblObs = new Label(c.getObservacion());
            lblObs.setWrapText(true);
            lblObs.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
            raiz.getChildren().addAll(lblObsTitulo, lblObs);
        }

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #374151;"
                + " -fx-border-color: #E2E8F0; -fx-border-radius: 6;"
                + " -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 20;");
        btnCerrar.setOnAction(e -> stage.close());
        raiz.getChildren().add(btnCerrar);

        ScrollPane scroll = new ScrollPane(raiz);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white; -fx-background: white;");

        stage.setScene(new Scene(scroll, 460, 640));
        stage.showAndWait();
    }

    private HBox filaDetalle(String etiqueta, String valor) {
        Label lE = new Label(etiqueta);
        lE.setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
        javafx.scene.layout.Region esp = new javafx.scene.layout.Region();
        HBox.setHgrow(esp, javafx.scene.layout.Priority.ALWAYS);
        Label lV = new Label(valor);
        lV.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        HBox h = new HBox(8, lE, esp, lV);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return h;
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