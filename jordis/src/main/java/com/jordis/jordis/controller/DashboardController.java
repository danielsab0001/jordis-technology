package com.jordis.jordis.controller;

import com.jordis.jordis.model.Producto;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.DashboardService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    // Tarjetas principales
    @FXML private Label lblFecha;
    @FXML private Label lblVentasHoy;
    @FXML private Label lblVariacion;
    @FXML private Label lblTransacciones;
    @FXML private Label lblVentasMes;
    @FXML private Label lblCreditosPendientes;

    // Tarjetas de alertas
    @FXML private Label lblStockBajo;
    @FXML private Label lblCreditosVencidos;
    @FXML private Label lblCuentasPorPagar;
    @FXML private Label lblAlertas;

    // Cards clickeables
    @FXML private VBox cardStockBajo;
    @FXML private VBox cardCreditosVencidos;
    @FXML private VBox cardCuentasPorPagar;
    @FXML private VBox cardAlertas;

    // Gráfica y tabla
    @FXML private BarChart<String, Number> graficaVentas;
    @FXML private VBox panelTopProductos;
    @FXML private TableView<Venta> tablaUltimasVentas;
    @FXML private TableColumn<Venta, String> colFactura;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colProductos;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colPago;

    private final DashboardService dashboardService;

    // Referencia al MainController para navegar desde las tarjetas
    private MainController mainController;

    private static final DateTimeFormatter FMT_DIA =
            DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FMT_LARGO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    @FXML
    public void initialize() {
        configurarTabla();
        cargarDatos();
        configurarNavegacionTarjetas();
    }

    private void configurarTabla() {
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
                    c != null ? c.getNombreCompleto() : "Ocasional");
        });
        colProductos.setCellValueFactory(d -> {
            String res = d.getValue().getDetalles().stream()
                    .map(vp -> vp.getProducto().getNombre()
                            + " x" + vp.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(res.isEmpty() ? "—" : res);
        });
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getTotal().toPlainString()));
        colPago.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getMetodoPago()));
    }

    private void cargarDatos() {
        // Fecha actual
        lblFecha.setText(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM yyyy",
                        new java.util.Locale("es"))));

        // Tarjetas principales
        BigDecimal ventasHoy = dashboardService.getTotalVentasHoy();
        lblVentasHoy.setText("RD$" + ventasHoy.toPlainString());

        double variacion = dashboardService.getVariacionVentasHoy();
        if (variacion > 0) {
            lblVariacion.setText("▲ +" + String.format("%.1f", variacion)
                    + "% vs ayer");
            lblVariacion.setStyle("-fx-font-size: 11; -fx-text-fill: #15803D;");
        } else if (variacion < 0) {
            lblVariacion.setText("▼ " + String.format("%.1f", variacion)
                    + "% vs ayer");
            lblVariacion.setStyle("-fx-font-size: 11; -fx-text-fill: #DC2626;");
        } else {
            lblVariacion.setText("= igual que ayer");
            lblVariacion.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
        }

        lblTransacciones.setText(
                String.valueOf(dashboardService.getTransaccionesHoy()));
        lblVentasMes.setText(
                "RD$" + dashboardService.getTotalVentasMes().toPlainString());
        lblCreditosPendientes.setText(
                "RD$" + dashboardService.getTotalCreditosPendientes().toPlainString());

        // Tarjetas de alertas
        long stockBajo = dashboardService.getProductosStockBajo();
        lblStockBajo.setText(stockBajo + " producto"
                + (stockBajo == 1 ? "" : "s"));

        long creditosVencidos = dashboardService.getCreditosVencidos();
        lblCreditosVencidos.setText(String.valueOf(creditosVencidos));

        lblCuentasPorPagar.setText(
                "RD$" + dashboardService.getTotalCuentasPorPagar().toPlainString());

        lblAlertas.setText(
                String.valueOf(dashboardService.getAlertasNoLeidas()));

        // Gráfica
        cargarGrafica();

        // Top productos
        cargarTopProductos();

        // Últimas ventas
        tablaUltimasVentas.setItems(FXCollections.observableArrayList(
                dashboardService.getUltimasVentas()));
    }

    private void cargarGrafica() {
        graficaVentas.getData().clear();
        graficaVentas.setLegendVisible(false);
        graficaVentas.getYAxis().setLabel("RD$");

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        Map<LocalDate, BigDecimal> datos =
                dashboardService.getVentasUltimos7Dias();

        datos.forEach((fecha, total) -> {
            String etiqueta = fecha.equals(LocalDate.now())
                    ? "Hoy" : fecha.format(FMT_DIA);
            XYChart.Data<String, Number> dato =
                    new XYChart.Data<>(etiqueta, total.doubleValue());
            serie.getData().add(dato);
        });

        graficaVentas.getData().add(serie);

        // Colorear barras en azul
        serie.getData().forEach(dato ->
                dato.getNode().setStyle("-fx-bar-fill: #2563EB;"));
    }

    private void cargarTopProductos() {
        panelTopProductos.getChildren().clear();
        List<Object[]> top = dashboardService.getTopProductosMes();

        if (top.isEmpty()) {
            Label sinDatos = new Label("Sin ventas este mes");
            sinDatos.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12;");
            panelTopProductos.getChildren().add(sinDatos);
            return;
        }

        // Calcular max para la barra proporcional
        long maxUnidades = ((Number) top.get(0)[1]).longValue();

        for (int i = 0; i < top.size(); i++) {
            Object[] row    = top.get(i);
            Producto prod   = (Producto) row[0];
            long unidades   = ((Number) row[1]).longValue();
            BigDecimal ingresos = (BigDecimal) row[2];

            VBox fila = new VBox(3);

            HBox info = new HBox(8);
            info.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label num = new Label((i + 1) + ".");
            num.setStyle("-fx-font-size: 11; -fx-text-fill: #94A3B8;"
                    + " -fx-min-width: 18;");
            Label nombre = new Label(prod.getNombre());
            nombre.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                    + " -fx-text-fill: #0F172A;");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            Label unidadesLbl = new Label(unidades + " u.");
            unidadesLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
            info.getChildren().addAll(num, nombre, espacio, unidadesLbl);

            // Barra proporcional
            double pct = maxUnidades > 0
                    ? (double) unidades / maxUnidades : 0;
            HBox barraContenedor = new HBox();
            barraContenedor.setStyle(
                    "-fx-background-color: #EFF6FF; -fx-background-radius: 4;");
            barraContenedor.setPrefHeight(6);

            Region barra = new Region();
            barra.setPrefHeight(6);
            barra.setStyle("-fx-background-color: #2563EB;"
                    + " -fx-background-radius: 4;");
            barra.setPrefWidth(200 * pct);
            barraContenedor.getChildren().add(barra);

            Label ingresosLbl = new Label(
                    "RD$" + ingresos.toPlainString());
            ingresosLbl.setStyle(
                    "-fx-font-size: 11; -fx-text-fill: #2563EB;");

            fila.getChildren().addAll(info, barraContenedor, ingresosLbl);
            panelTopProductos.getChildren().add(fila);

            if (i < top.size() - 1) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #F1F5F9;");
                panelTopProductos.getChildren().add(sep);
            }
        }
    }

    private void configurarNavegacionTarjetas() {
        cardStockBajo.setOnMouseClicked(e -> {
            if (mainController != null) mainController.onInventario();
        });
        cardCreditosVencidos.setOnMouseClicked(e -> {
            if (mainController != null) mainController.onCreditos();
        });
        cardCuentasPorPagar.setOnMouseClicked(e -> {
            if (mainController != null) mainController.onCuentasPorPagar();
        });
        cardAlertas.setOnMouseClicked(e -> {
            if (mainController != null) mainController.onAlertas();
        });
    }

    @FXML
    public void onActualizar() {
        cargarDatos();
    }
}