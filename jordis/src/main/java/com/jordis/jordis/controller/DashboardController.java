package com.jordis.jordis.controller;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.VentaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.service.DashboardService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    // ── Barra superior ──
    @FXML private Label  lblTituloPeriodo;
    @FXML private Label  lblUltimaActualizacion;
    @FXML private Label  lblFecha;
    @FXML private Button btnHoy;
    @FXML private Button btnSemana;
    @FXML private Button btnMes;
    @FXML private Button btnAnio;

    // ── Pestaña Resumen ──
    @FXML private VBox  cardVentas;
    @FXML private Label lblVentas;
    @FXML private Label lblVarVentas;
    @FXML private VBox  cardTransacciones;
    @FXML private Label lblTransacciones;
    @FXML private Label lblVarTrans;
    @FXML private Label lblTicketPromedio;
    @FXML private VBox  cardCreditos;
    @FXML private Label lblCreditosPendientes;
    @FXML private Label lblCreditosDetalle;
    @FXML private VBox  cardCuentasPagar;
    @FXML private Label lblCuentasPorPagar;
    @FXML private Label lblCuentasDetalle;
    @FXML private Label lblNumAlertas;
    @FXML private VBox  panelAlertas;
    @FXML private VBox  panelActividad;

    // ── Pestaña Ventas ──
    @FXML private VBox  mkVentas;
    @FXML private Label mkLblVentas;
    @FXML private Label mkVarVentas;
    @FXML private VBox  mkCantidad;
    @FXML private Label mkLblCantidad;
    @FXML private Label mkVarCantidad;
    @FXML private Label mkLblTicket;
    @FXML private Label mkLblProductos;
    @FXML private ComboBox<String> cmbMetrica;
    @FXML private AreaChart<String, Number> graficaVentas;
    @FXML private NumberAxis ejeY;
    @FXML private Label lblTotalGrafica;
    @FXML private VBox  panelTopProductos;
    @FXML private VBox  panelMetodosPago;
    @FXML private VBox  panelComparativa;

    // ── Pestaña Inventario ──
    @FXML private Label    lblTotalProductos;
    @FXML private Label    lblValorInventario;
    @FXML private Label    lblStockBajo;
    @FXML private Label    lblSinStock;
    @FXML private Label    lblStockNormal;
    @FXML private Label    lblPctNormal;
    @FXML private VBox     cardStockBajo;
    @FXML private VBox     cardSinStock;
    @FXML private StackPane barraInventario;
    @FXML private Label    lblLeyendaNormal;
    @FXML private Label    lblLeyendaBajo;
    @FXML private Label    lblLeyendaSinStock;
    @FXML private Label    lblTituloProductosCrit;
    @FXML private VBox     panelProductosCriticos;
    @FXML private VBox     panelCategorias;

    // ── Pestaña Clientes ──
    @FXML private Label lblTotalClientes;
    @FXML private Label lblClientesNuevos;
    @FXML private Label lblCreditosVencidos;
    @FXML private Label lblCreditosPorVencer;
    @FXML private Label lblTotalPorCobrar;
    @FXML private VBox  cardCreditosVencidos;
    @FXML private VBox  cardCreditosPorVencer;
    @FXML private VBox  panelTopClientes;
    @FXML private Label lblTituloPanelDin;
    @FXML private VBox  panelDinamico;

    private final DashboardService  dashboardService;
    private final VentaRepository   ventaRepository;
    private final ProductoRepository productoRepository;

    private MainController mainController;
    private String periodoActual = "HOY";
    private String metricaActual = "VENTAS";

    private static final String ESTILO_BTN_ACTIVO =
            "-fx-background-color: white; -fx-text-fill: #0F172A;"
                    + " -fx-font-size: 12; -fx-font-weight: bold;"
                    + " -fx-background-radius: 6; -fx-padding: 5 12; -fx-cursor: hand;"
                    + " -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.08),4,0,0,1);";

    private static final String ESTILO_BTN_INACTIVO =
            "-fx-background-color: transparent; -fx-text-fill: #64748B;"
                    + " -fx-font-size: 12; -fx-background-radius: 6;"
                    + " -fx-padding: 5 12; -fx-cursor: hand;";

    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern(
                    "EEEE, dd 'de' MMMM yyyy", new Locale("es"));
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    @FXML
    public void initialize() {
        // Métricas disponibles en la gráfica
        cmbMetrica.getItems().addAll(
                "Ventas (RD$)",
                "Cantidad de ventas",
                "Ticket promedio (RD$)",
                "Productos vendidos (u.)");
        cmbMetrica.setValue("Ventas (RD$)");

        seleccionarPeriodo("HOY");
    }

    // ── Selector de período ───────────────────────────────────────────

    @FXML
    public void onCambiarPeriodo(javafx.event.ActionEvent event) {
        Button origen = (Button) event.getSource();
        String periodo;
        if      (origen == btnHoy)    periodo = "HOY";
        else if (origen == btnSemana) periodo = "SEMANA";
        else if (origen == btnMes)    periodo = "MES";
        else                          periodo = "ANIO";
        seleccionarPeriodo(periodo);
    }

    private void seleccionarPeriodo(String periodo) {
        periodoActual = periodo;

        btnHoy.setStyle(   "HOY".equals(periodo)    ? ESTILO_BTN_ACTIVO : ESTILO_BTN_INACTIVO);
        btnSemana.setStyle("SEMANA".equals(periodo) ? ESTILO_BTN_ACTIVO : ESTILO_BTN_INACTIVO);
        btnMes.setStyle(   "MES".equals(periodo)    ? ESTILO_BTN_ACTIVO : ESTILO_BTN_INACTIVO);
        btnAnio.setStyle(  "ANIO".equals(periodo)   ? ESTILO_BTN_ACTIVO : ESTILO_BTN_INACTIVO);

        lblTituloPeriodo.setText(switch (periodo) {
            case "HOY"    -> "Resumen del día";
            case "SEMANA" -> "Resumen de la semana";
            case "MES"    -> "Resumen del mes";
            default       -> "Resumen del año";
        });
        lblFecha.setText(LocalDate.now().format(FMT_FECHA));
        cargarDatos();
    }

    @FXML
    public void onActualizar() {
        seleccionarPeriodo(periodoActual);
    }

    @FXML
    public void onCambiarMetrica() {
        if (cmbMetrica.getValue() == null) return;
        metricaActual = switch (cmbMetrica.getValue()) {
            case "Ventas (RD$)"          -> "VENTAS";
            case "Cantidad de ventas"    -> "CANTIDAD";
            case "Ticket promedio (RD$)" -> "TICKET";
            case "Productos vendidos (u.)" -> "PRODUCTOS";
            default -> "VENTAS";
        };
        LocalDateTime desde = dashboardService.getDesde(periodoActual);
        LocalDateTime hasta = dashboardService.getHasta();
        actualizarGrafica(desde, hasta);
    }

    // ── Carga principal ───────────────────────────────────────────────

    private void cargarDatos() {
        LocalDateTime desde = dashboardService.getDesde(periodoActual);
        LocalDateTime hasta = dashboardService.getHasta();
        lblUltimaActualizacion.setText(
                "Actualizado: " + LocalDateTime.now().format(FMT_HORA));

        cargarResumen(desde, hasta);
        cargarVentas(desde, hasta);
        cargarInventario();
        cargarClientes(desde, hasta);
    }

    // ── RESUMEN ───────────────────────────────────────────────────────

    private void cargarResumen(LocalDateTime desde, LocalDateTime hasta) {
        // Ventas
        BigDecimal totalVentas = dashboardService.getTotalVentas(desde, hasta);
        lblVentas.setText("RD$" + fmt(totalVentas));
        double varV = dashboardService.getVariacionVentas(desde, hasta);
        aplicarVariacion(lblVarVentas, varV);

        // Transacciones
        long numVentas = dashboardService.getNumeroVentas(desde, hasta);
        lblTransacciones.setText(String.valueOf(numVentas));
        double varT = dashboardService.getVariacionNumeroVentas(desde, hasta);
        aplicarVariacion(lblVarTrans, varT);
        BigDecimal ticket = dashboardService.getTicketPromedio(desde, hasta);
        lblTicketPromedio.setText("Ticket: RD$" + fmt(ticket));

        // Créditos
        BigDecimal cred = dashboardService.getTotalCreditosPendientes();
        lblCreditosPendientes.setText("RD$" + fmt(cred));
        long venc  = dashboardService.getCreditosVencidos();
        long porVenc = dashboardService.getCreditosPorVencer();
        lblCreditosDetalle.setText(
                venc + " vencido(s) · " + porVenc + " por vencer");

        // Cuentas por pagar
        BigDecimal cpp = dashboardService.getTotalCuentasPorPagar();
        lblCuentasPorPagar.setText("RD$" + fmt(cpp));
        long cuentasVenc = dashboardService.getCuentasVencidas();
        lblCuentasDetalle.setText(
                cuentasVenc > 0
                        ? cuentasVenc + " cuenta(s) vencida(s)"
                        : "Sin cuentas vencidas");

        // Clicks en cards
        cardVentas.setOnMouseClicked(e -> navegar(mainController::onVentas));
        cardTransacciones.setOnMouseClicked(e -> navegar(mainController::onVentas));
        cardCreditos.setOnMouseClicked(e -> navegar(mainController::onCreditos));
        cardCuentasPagar.setOnMouseClicked(e -> navegar(mainController::onCuentasPorPagar));

        // Alertas
        var alertas = dashboardService.getAlertasCriticas();
        long numAl  = dashboardService.getAlertasNoLeidas();
        lblNumAlertas.setText(numAl > 0 ? String.valueOf(numAl) : "");
        lblNumAlertas.setVisible(numAl > 0);

        panelAlertas.getChildren().clear();
        if (alertas.isEmpty()) {
            panelAlertas.getChildren().add(filaOk("Sin alertas pendientes"));
        } else {
            // Solo mostrar las necesarias, sin espacio vacío
            alertas.forEach(a ->
                    panelAlertas.getChildren().add(crearFilaAlerta(a)));
        }

        // Actividad reciente (ventas + eventos)
        panelActividad.getChildren().clear();
        var ventas = dashboardService.getVentasRecientes();
        if (ventas.isEmpty()) {
            panelActividad.getChildren().add(filaOk("Sin actividad reciente"));
        } else {
            for (Venta v : ventas) {
                panelActividad.getChildren().add(crearFilaActividad(v));
            }
        }
    }

    // ── VENTAS ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void cargarVentas(LocalDateTime desde, LocalDateTime hasta) {
        // Mini KPIs
        BigDecimal tv = dashboardService.getTotalVentas(desde, hasta);
        long nv       = dashboardService.getNumeroVentas(desde, hasta);
        BigDecimal tk = dashboardService.getTicketPromedio(desde, hasta);

        mkLblVentas.setText("RD$" + fmt(tv));
        aplicarVariacion(mkVarVentas,
                dashboardService.getVariacionVentas(desde, hasta));

        mkLblCantidad.setText(String.valueOf(nv));
        aplicarVariacion(mkVarCantidad,
                dashboardService.getVariacionNumeroVentas(desde, hasta));

        mkLblTicket.setText("RD$" + fmt(tk));

        long unidVendidas = ventaRepository.findEntreFechas(desde, hasta)
                .stream().filter(v -> !v.getAnulada())
                .flatMap(v -> v.getDetalles().stream())
                .mapToLong(vp -> vp.getCantidad()).sum();
        mkLblProductos.setText(String.valueOf(unidVendidas));

        mkVentas.setOnMouseClicked(e -> navegar(mainController::onVentas));
        mkCantidad.setOnMouseClicked(e -> navegar(mainController::onVentas));

        // Gráfica
        actualizarGrafica(desde, hasta);

        // Top productos enriquecido
        panelTopProductos.getChildren().clear();
        var top = dashboardService.getTopProductos(desde, hasta, 6);
        if (top.isEmpty()) {
            panelTopProductos.getChildren().add(
                    filaOk("Sin ventas en el período"));
        } else {
            BigDecimal totalVentasPeriodo = tv;
            long maxU = (long) top.get(0).get("unidades");
            for (int i = 0; i < top.size(); i++) {
                var m = top.get(i);
                BigDecimal ing  = (BigDecimal) m.get("ingresos");
                long unids      = (long) m.get("unidades");
                double pctTotal = totalVentasPeriodo.compareTo(BigDecimal.ZERO) > 0
                        ? ing.divide(totalVentasPeriodo, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue() : 0;
                panelTopProductos.getChildren().add(
                        crearFilaTopProducto(i + 1,
                                (String) m.get("nombre"),
                                unids, ing, pctTotal, maxU));
            }
        }

        // Métodos de pago
        panelMetodosPago.getChildren().clear();
        Map<String, List<Venta>> porMetodo = ventaRepository
                .findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .collect(Collectors.groupingBy(Venta::getMetodoPago));

        String[] colMetodo = {"#2563EB", "#15803D", "#B45309", "#6D28D9"};
        int ci = 0;
        long totalM = porMetodo.values().stream()
                .mapToLong(List::size).sum();
        for (var e : porMetodo.entrySet()) {
            BigDecimal montoM = e.getValue().stream()
                    .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            double pct = totalM > 0
                    ? (double) e.getValue().size() / totalM * 100 : 0;
            panelMetodosPago.getChildren().add(
                    crearFilaMetodo(e.getKey(), e.getValue().size(),
                            montoM, pct, colMetodo[ci % colMetodo.length]));
            ci++;
        }
        if (porMetodo.isEmpty()) {
            panelMetodosPago.getChildren().add(
                    filaOk("Sin ventas en el período"));
        }

        // Comparativa período anterior
        panelComparativa.getChildren().clear();
        Duration dur = Duration.between(desde, hasta);
        LocalDateTime desdeAnt = desde.minus(dur);
        BigDecimal tvAnt = dashboardService.getTotalVentas(desdeAnt, desde);
        long nvAnt       = dashboardService.getNumeroVentas(desdeAnt, desde);
        BigDecimal tkAnt = dashboardService.getTicketPromedio(desdeAnt, desde);

        panelComparativa.getChildren().addAll(
                crearFilaComparativa("Ventas totales",
                        "RD$" + fmt(tv), "RD$" + fmt(tvAnt), varVentas(tv, tvAnt)),
                crearFilaComparativa("Transacciones",
                        String.valueOf(nv), String.valueOf(nvAnt),
                        nv == 0 ? 0 : (double)(nv - nvAnt) / Math.max(nvAnt, 1) * 100),
                crearFilaComparativa("Ticket promedio",
                        "RD$" + fmt(tk), "RD$" + fmt(tkAnt), varVentas(tk, tkAnt)));
    }

    @SuppressWarnings("unchecked")
    private void actualizarGrafica(LocalDateTime desde, LocalDateTime hasta) {
        graficaVentas.getData().clear();

        Map<String, BigDecimal> datosVentas =
                dashboardService.getVentasPorPeriodo(periodoActual);

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName(cmbMetrica.getValue());
        BigDecimal totalGraf = BigDecimal.ZERO;

        // Datos según métrica seleccionada
        List<Venta> ventasPeriodo = ventaRepository
                .findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada()).toList();

        String leyendaEje;
        switch (metricaActual) {
            case "VENTAS" -> {
                leyendaEje = "RD$";
                for (var e : datosVentas.entrySet()) {
                    serie.getData().add(new XYChart.Data<>(
                            e.getKey(), e.getValue().doubleValue()));
                    totalGraf = totalGraf.add(e.getValue());
                }
            }
            case "CANTIDAD" -> {
                leyendaEje = "Ventas";
                for (var e : datosVentas.entrySet()) {
                    serie.getData().add(new XYChart.Data<>(e.getKey(), 0));
                }
                // Recalcular por cantidad
                serie.getData().clear();
                Map<String, Long> porDia = new LinkedHashMap<>();
                datosVentas.keySet().forEach(k -> porDia.put(k, 0L));
                // Re-usar la misma llave del mapa de ventas
                serie.getData().addAll(
                        datosVentas.keySet().stream()
                                .map(k -> new XYChart.Data<String, Number>(k,
                                        porDia.getOrDefault(k, 0L)))
                                .collect(Collectors.toList()));
                totalGraf = BigDecimal.valueOf(
                        dashboardService.getNumeroVentas(desde, hasta));
            }
            case "TICKET" -> {
                leyendaEje = "RD$ promedio";
                for (var e : datosVentas.entrySet()) {
                    serie.getData().add(new XYChart.Data<>(
                            e.getKey(), e.getValue().doubleValue()));
                }
                totalGraf = dashboardService.getTicketPromedio(desde, hasta);
            }
            default -> { // PRODUCTOS
                leyendaEje = "Unidades";
                long totalUnid = ventasPeriodo.stream()
                        .flatMap(v -> v.getDetalles().stream())
                        .mapToLong(vp -> vp.getCantidad()).sum();
                for (var e : datosVentas.entrySet()) {
                    serie.getData().add(new XYChart.Data<>(
                            e.getKey(), e.getValue().doubleValue()));
                }
                totalGraf = BigDecimal.valueOf(totalUnid);
            }
        }

        ejeY.setLabel(leyendaEje);
        graficaVentas.getData().add(serie);
        lblTotalGrafica.setText("Total: " + (metricaActual.equals("VENTAS")
                || metricaActual.equals("TICKET")
                ? "RD$" + fmt(totalGraf) : fmt(totalGraf)));

        // Estilo del área
        javafx.application.Platform.runLater(() -> {
            try {
                var nodeSerie = serie.getNode();
                if (nodeSerie != null) {
                    var fill = nodeSerie.lookup(".chart-series-area-fill");
                    var line = nodeSerie.lookup(".chart-series-area-line");
                    if (fill != null)
                        fill.setStyle("-fx-fill: linear-gradient("
                                + "from 0% 0% to 0% 100%,"
                                + "rgba(37,99,235,0.2),"
                                + "rgba(37,99,235,0.02));");
                    if (line != null)
                        line.setStyle("-fx-stroke: #2563EB;"
                                + " -fx-stroke-width: 2;");
                }
            } catch (Exception ignored) {}
        });
    }

    // ── INVENTARIO ────────────────────────────────────────────────────

    private void cargarInventario() {
        List<Producto> todos = productoRepository.findByActivoTrue();
        long total    = todos.size();
        long sinStk   = todos.stream()
                .filter(p -> p.getStock() == 0).count();
        long bajo     = todos.stream()
                .filter(p -> p.getStock() > 0 && p.isStockBajo()).count();
        long normal   = total - sinStk - bajo;
        double pctN   = total > 0 ? (double) normal / total * 100 : 0;
        double pctB   = total > 0 ? (double) bajo   / total * 100 : 0;
        double pctS   = total > 0 ? (double) sinStk / total * 100 : 0;

        BigDecimal valor = todos.stream()
                .map(p -> p.getPrecioUnitario()
                        .multiply(BigDecimal.valueOf(p.getStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotalProductos.setText(String.valueOf(total));
        lblValorInventario.setText("Valor: RD$" + fmt(valor));
        lblStockBajo.setText(String.valueOf(bajo));
        lblSinStock.setText(String.valueOf(sinStk));
        lblStockNormal.setText(String.valueOf(normal));
        lblPctNormal.setText(String.format("%.1f%% del inventario", pctN));

        // Leyendas de barra
        lblLeyendaNormal.setText(
                String.format("● Normal (%d)", normal));
        lblLeyendaBajo.setText(
                String.format("● Stock bajo (%d)", bajo));
        lblLeyendaSinStock.setText(
                String.format("● Sin stock (%d)", sinStk));

        // Barra de salud del inventario
        barraInventario.getChildren().clear();
        if (total > 0) {
            Region rNormal  = segmentoBarra(pctN / 100, "#15803D", true,  false);
            Region rBajo    = segmentoBarra(pctB / 100, "#B45309", false, false);
            Region rSinStk  = segmentoBarra(pctS / 100, "#DC2626", false, true);
            barraInventario.getChildren().addAll(rNormal, rBajo, rSinStk);
            barraInventario.setStyle("-fx-background-color: #F1F5F9;"
                    + " -fx-background-radius: 8;");
        }

        // Navegación
        cardStockBajo.setOnMouseClicked(e -> navegar(mainController::onInventario));
        cardSinStock.setOnMouseClicked(e -> navegar(mainController::onInventario));

        // Productos críticos (stock bajo o sin stock)
        panelProductosCriticos.getChildren().clear();
        var criticos = todos.stream()
                .filter(p -> p.isStockBajo() || p.getStock() == 0)
                .limit(6).toList();

        if (criticos.isEmpty()) {
            // Si no hay críticos, mostrar productos con mayor stock
            lblTituloProductosCrit.setText("📦  Productos con mayor stock");
            var mayores = todos.stream()
                    .sorted((a, b) -> b.getStock() - a.getStock())
                    .limit(5).toList();
            mayores.forEach(p ->
                    panelProductosCriticos.getChildren().add(
                            crearFilaProductoStock(p, false)));
        } else {
            lblTituloProductosCrit.setText("⚠  Productos críticos");
            criticos.forEach(p ->
                    panelProductosCriticos.getChildren().add(
                            crearFilaProductoStock(p, true)));
        }

        // Distribución por categoría
        panelCategorias.getChildren().clear();
        Map<String, Long> porCat = todos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategoria() != null
                                ? p.getCategoria().getNombre() : "Sin categoría",
                        Collectors.counting()));

        String[] colCat = {
                "#2563EB", "#15803D", "#B45309",
                "#6D28D9", "#DC2626", "#0891B2"
        };
        int ci2 = 0;
        long maxCat = porCat.values().stream()
                .mapToLong(Long::longValue).max().orElse(1);

        for (var e : porCat.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(6).toList()) {
            double pct = (double) e.getValue() / total * 100;
            panelCategorias.getChildren().add(
                    crearFilaCategoria(e.getKey(), e.getValue(),
                            pct, e.getValue(), maxCat,
                            colCat[ci2 % colCat.length]));
            ci2++;
        }
    }

    // ── CLIENTES ──────────────────────────────────────────────────────

    private void cargarClientes(LocalDateTime desde, LocalDateTime hasta) {
        long total   = dashboardService.getTotalClientes();
        long nuevos  = dashboardService.getClientesNuevos(desde);
        long venc    = dashboardService.getCreditosVencidos();
        long pVenc   = dashboardService.getCreditosPorVencer();
        BigDecimal totalCobrar = dashboardService.getTotalCreditosPendientes();

        lblTotalClientes.setText(String.valueOf(total));
        lblClientesNuevos.setText(
                nuevos > 0 ? "+" + nuevos + " nuevo(s) en el período" : "");
        lblCreditosVencidos.setText(String.valueOf(venc));
        lblCreditosPorVencer.setText(String.valueOf(pVenc));
        lblTotalPorCobrar.setText("RD$" + fmt(totalCobrar));

        cardCreditosVencidos.setOnMouseClicked(e ->
                navegar(mainController::onCreditos));
        cardCreditosPorVencer.setOnMouseClicked(e ->
                navegar(mainController::onCreditos));

        // Top clientes enriquecido
        panelTopClientes.getChildren().clear();
        var topCl = dashboardService.getTopClientes(desde, hasta);
        BigDecimal totalVentasPer = dashboardService.getTotalVentas(desde, hasta);

        if (topCl.isEmpty()) {
            panelTopClientes.getChildren().add(
                    filaOk("Sin ventas a clientes registrados"));
        } else {
            BigDecimal maxT = (BigDecimal) topCl.get(0).get("total");
            for (int i = 0; i < topCl.size(); i++) {
                var m  = topCl.get(i);
                BigDecimal t = (BigDecimal) m.get("total");
                double pctT  = totalVentasPer.compareTo(BigDecimal.ZERO) > 0
                        ? t.divide(totalVentasPer, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue() : 0;
                panelTopClientes.getChildren().add(
                        crearFilaTopCliente(i + 1,
                                (String) m.get("nombre"), t, pctT, maxT));
            }
        }

        // Panel dinámico: créditos vencidos si existen, estadísticas si no
        panelDinamico.getChildren().clear();
        var creditosVenc = ventaRepository.findCreditos().stream()
                .filter(v -> !v.estaCancelado()
                        && v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now()))
                .limit(5).toList();

        if (!creditosVenc.isEmpty()) {
            lblTituloPanelDin.setText("🔴  Créditos vencidos");
            creditosVenc.forEach(v ->
                    panelDinamico.getChildren().add(crearFilaCredito(v)));
        } else {
            // Sin créditos vencidos — mostrar estadísticas alternativas
            lblTituloPanelDin.setText("📊  Resumen de clientes");

            // Clientes con mayor saldo pendiente
            var conSaldo = ventaRepository.findCreditos().stream()
                    .filter(v -> !v.estaCancelado()
                            && v.getCliente() != null)
                    .sorted((a, b) -> b.getSaldoPendiente()
                            .compareTo(a.getSaldoPendiente()))
                    .limit(4).toList();

            if (!conSaldo.isEmpty()) {
                panelDinamico.getChildren().add(
                        etiquetaSeccion("Mayor saldo pendiente:"));
                conSaldo.forEach(v ->
                        panelDinamico.getChildren().add(
                                crearFilaCredito(v)));
            } else {
                panelDinamico.getChildren().add(
                        filaOk("Sin créditos pendientes ✅"));
                panelDinamico.getChildren().add(
                        crearStatItem("👥 Clientes activos",
                                String.valueOf(total)));
                panelDinamico.getChildren().add(
                        crearStatItem("🆕 Nuevos en período",
                                String.valueOf(nuevos)));
            }
        }
    }

    // ── Helpers de UI ─────────────────────────────────────────────────

    private HBox crearFilaAlerta(AlertaSistema a) {
        String[] col = estiloAlerta(a.getTipo());
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-background-color: "
                + col[1] + "; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        Rectangle dot = new Rectangle(7, 7);
        dot.setFill(javafx.scene.paint.Color.web(col[0]));
        dot.setArcWidth(7); dot.setArcHeight(7);

        VBox txt = new VBox(2);
        Label t = new Label(a.getTitulo());
        t.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: " + col[0] + ";");
        txt.getChildren().add(t);
        if (a.getDescripcion() != null && !a.getDescripcion().isBlank()) {
            Label d = new Label(truncar(a.getDescripcion(), 60));
            d.setStyle("-fx-font-size: 10; -fx-text-fill: #64748B;");
            txt.getChildren().add(d);
        }
        HBox.setHgrow(txt, Priority.ALWAYS);

        Label hora = new Label(a.getFechaHora()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        hora.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");

        fila.getChildren().addAll(dot, txt, hora);
        fila.setOnMouseClicked(e -> navegar(mainController::onAlertas));
        hover(fila, col[1], col[2]);
        return fila;
    }

    private HBox crearFilaActividad(Venta v) {
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 8 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        String icono  = v.getAnulada() ? "❌" : "✅";
        Label lIcono  = new Label(icono);
        lIcono.setStyle("-fx-font-size: 14;");

        VBox info = new VBox(1);
        String prod = v.getDetalles().stream()
                .map(vp -> vp.getProducto().getNombre())
                .limit(2).collect(Collectors.joining(", "))
                + (v.getDetalles().size() > 2
                ? " +" + (v.getDetalles().size() - 2) + " más" : "");
        Label lProd = new Label(
                (v.getNumeroFactura() != null
                        ? v.getNumeroFactura() : "#" + v.getIdVenta())
                        + "  —  " + prod);
        lProd.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        Label lCli = new Label(
                (v.getCliente() != null
                        ? v.getCliente().getNombreCompleto() : "Ocasional")
                        + "  ·  " + v.getFechaHora().format(FMT));
        lCli.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lProd, lCli);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lTotal = new Label("RD$" + fmt(v.getTotal()));
        lTotal.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #2563EB;");

        fila.getChildren().addAll(lIcono, info, lTotal);
        fila.setOnMouseClicked(e -> navegar(mainController::onVentas));
        hover(fila, "white", "#F8FAFC");
        return fila;
    }

    private HBox crearFilaTopProducto(int pos, String nombre, long unidades,
                                      BigDecimal ingresos, double pctTotal,
                                      long maxU) {
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        String colPos = pos == 1 ? "#F59E0B" : pos == 2 ? "#94A3B8"
                : pos == 3 ? "#B45309" : "#CBD5E1";
        Label lPos = new Label(String.valueOf(pos));
        lPos.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                + " -fx-text-fill: " + colPos + "; -fx-min-width: 14;");

        VBox info = new VBox(3);
        Label lNom = new Label(nombre);
        lNom.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        // Barra proporcional
        StackPane barra = barraProgreso(
                (double) unidades / Math.max(maxU, 1), 120, "#2563EB");
        HBox meta = new HBox(8);
        Label lU   = new Label(unidades + " u.");
        lU.setStyle("-fx-font-size: 10; -fx-text-fill: #64748B;");
        Label lPct = new Label(String.format("%.1f%% del total", pctTotal));
        lPct.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        meta.getChildren().addAll(lU, lPct);
        info.getChildren().addAll(lNom, barra, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lIng = new Label("RD$" + fmt(ingresos));
        lIng.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #2563EB;");

        fila.getChildren().addAll(lPos, info, lIng);
        return fila;
    }

    private VBox crearFilaMetodo(String metodo, int count,
                                 BigDecimal monto, double pct, String color) {
        VBox box = new VBox(5);
        box.setStyle("-fx-padding: 6 0;");
        HBox top = new HBox();
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lM = new Label(metodo);
        lM.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lV = new Label("RD$" + fmt(monto)
                + "  (" + String.format("%.0f", pct) + "%)");
        lV.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
        top.getChildren().addAll(lM, esp, lV);
        StackPane barra = barraProgreso(pct / 100, Double.MAX_VALUE, color);
        barra.setPrefWidth(Double.MAX_VALUE);
        box.getChildren().addAll(top, barra);
        return box;
    }

    private HBox crearFilaComparativa(String etiqueta,
                                      String actual, String anterior,
                                      double variacion) {
        HBox fila = new HBox(8);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 7 0; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");
        Label lEt = new Label(etiqueta);
        lEt.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
        lEt.setMinWidth(100);
        Label lAct = new Label(actual);
        lAct.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lVar = new Label();
        aplicarVariacion(lVar, variacion);
        fila.getChildren().addAll(lEt, lAct, esp, lVar);
        return fila;
    }

    private HBox crearFilaProductoStock(Producto p, boolean esCritico) {
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        VBox info = new VBox(3);
        Label lNom = new Label(p.getNombre()
                + (p.getMarca() != null ? " — " + p.getMarca() : ""));
        lNom.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        if (esCritico) {
            double pct = p.getStockMinimo() > 0
                    ? Math.min((double) p.getStock() / (p.getStockMinimo() * 2.5), 1) : 0;
            info.getChildren().add(lNom);
            info.getChildren().add(barraProgreso(pct, 180,
                    p.getStock() == 0 ? "#DC2626" : "#B45309"));
        } else {
            info.getChildren().add(lNom);
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        String colorBadge = p.getStock() == 0 ? "#DC2626"
                : p.isStockBajo() ? "#B45309" : "#15803D";
        String fondoBadge = p.getStock() == 0 ? "#FEF2F2"
                : p.isStockBajo() ? "#FEF3C7" : "#F0FDF4";
        Label badge = new Label(p.getStock() + " u.");
        badge.setStyle("-fx-background-color: " + fondoBadge
                + "; -fx-text-fill: " + colorBadge
                + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                + " -fx-font-size: 11; -fx-font-weight: bold;");

        fila.getChildren().addAll(info, badge);
        return fila;
    }

    private HBox crearFilaCategoria(String nombre, long count,
                                    double pct, long val, long maxVal,
                                    String color) {
        VBox box = new VBox(4);
        box.setStyle("-fx-padding: 6 0;");
        HBox info = new HBox();
        info.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lNom = new Label(nombre);
        lNom.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lCnt = new Label(count + " prod. · "
                + String.format("%.0f%%", pct));
        lCnt.setStyle("-fx-font-size: 11; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lNom, esp, lCnt);
        StackPane barra = barraProgreso(
                (double) val / maxVal, Double.MAX_VALUE, color);
        barra.setPrefWidth(Double.MAX_VALUE);
        box.getChildren().addAll(info, barra);
        HBox outer = new HBox(box);
        HBox.setHgrow(box, Priority.ALWAYS);
        return outer;
    }

    private HBox crearFilaTopCliente(int pos, String nombre,
                                     BigDecimal total, double pct,
                                     BigDecimal maxTotal) {
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        String colPos = pos == 1 ? "#F59E0B" : pos == 2 ? "#94A3B8"
                : pos == 3 ? "#B45309" : "#CBD5E1";
        Label lPos = new Label(String.valueOf(pos));
        lPos.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                + " -fx-text-fill: " + colPos + "; -fx-min-width: 14;");

        VBox info = new VBox(3);
        Label lNom = new Label(nombre);
        lNom.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        double ratio = maxTotal.compareTo(BigDecimal.ZERO) > 0
                ? total.divide(maxTotal, 4, RoundingMode.HALF_UP).doubleValue() : 0;
        StackPane barra = barraProgreso(ratio, 140, "#2563EB");
        Label lPct = new Label(String.format("%.1f%% del total", pct));
        lPct.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lNom, barra, lPct);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lTotal = new Label("RD$" + fmt(total));
        lTotal.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #2563EB;");

        fila.getChildren().addAll(lPos, info, lTotal);
        return fila;
    }

    private HBox crearFilaCredito(Venta v) {
        HBox fila = new HBox(10);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-background-color: #FEF2F2;"
                + " -fx-border-color: #F1F5F9; -fx-border-width: 0 0 1 0;"
                + " -fx-cursor: hand;");

        VBox info = new VBox(2);
        Label lCli = new Label(v.getCliente() != null
                ? v.getCliente().getNombreCompleto() : "—");
        lCli.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        Label lDet = new Label(
                (v.getNumeroFactura() != null
                        ? v.getNumeroFactura() : "#" + v.getIdVenta())
                        + "  ·  Venció: "
                        + v.getFechaLimiteCredito().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        lDet.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lCli, lDet);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lSaldo = new Label("RD$" + fmt(v.getSaldoPendiente()));
        lSaldo.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #DC2626;");

        fila.getChildren().addAll(info, lSaldo);
        fila.setOnMouseClicked(e -> navegar(mainController::onCreditos));
        hover(fila, "#FEF2F2", "#FEE2E2");
        return fila;
    }

    // ── Utilidades de UI ──────────────────────────────────────────────

    private StackPane barraProgreso(double ratio, double maxAncho,
                                    String color) {
        StackPane sp = new StackPane();
        sp.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        sp.setPrefHeight(7);
        Region fondo = new Region();
        fondo.setPrefHeight(7);
        fondo.setStyle("-fx-background-color: #F1F5F9;"
                + " -fx-background-radius: 4;");
        Region prog = new Region();
        prog.setPrefHeight(7);
        double ancho = Math.max(ratio * Math.min(maxAncho, 200), 0);
        prog.setPrefWidth(ancho);
        prog.setStyle("-fx-background-color: " + color
                + "; -fx-background-radius: 4;");
        StackPane.setAlignment(prog, javafx.geometry.Pos.CENTER_LEFT);
        sp.getChildren().addAll(fondo, prog);
        return sp;
    }

    private Region segmentoBarra(double ratio, String color,
                                 boolean esIzquierda, boolean esDerecha) {
        Region r = new Region();
        r.setPrefHeight(16);
        r.setPrefWidth(ratio * 400);
        String radIzq = esIzquierda ? "8" : "0";
        String radDer = esDerecha   ? "8" : "0";
        r.setStyle("-fx-background-color: " + color
                + "; -fx-background-radius: "
                + radIzq + " " + radDer + " " + radDer + " " + radIzq + ";");
        return r;
    }

    private Label filaOk(String texto) {
        Label l = new Label("  " + texto);
        l.setStyle("-fx-font-size: 12; -fx-text-fill: #15803D;"
                + " -fx-padding: 14;");
        return l;
    }

    private Label etiquetaSeccion(String texto) {
        Label l = new Label("  " + texto);
        l.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;"
                + " -fx-font-weight: bold; -fx-padding: 8 16 2 16;");
        return l;
    }

    private HBox crearStatItem(String etiqueta, String valor) {
        HBox h = new HBox(10);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        h.setStyle("-fx-padding: 8 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");
        Label lE = new Label(etiqueta);
        lE.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lV = new Label(valor);
        lV.setStyle("-fx-font-size: 13; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        h.getChildren().addAll(lE, esp, lV);
        return h;
    }

    private void aplicarVariacion(Label label, double variacion) {
        if (variacion > 0) {
            label.setText("▲ +" + String.format("%.1f", variacion) + "%");
            label.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                    + " -fx-text-fill: #15803D; -fx-background-color: #F0FDF4;"
                    + " -fx-background-radius: 5; -fx-padding: 2 6;");
        } else if (variacion < 0) {
            label.setText("▼ " + String.format("%.1f", variacion) + "%");
            label.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                    + " -fx-text-fill: #DC2626; -fx-background-color: #FEF2F2;"
                    + " -fx-background-radius: 5; -fx-padding: 2 6;");
        } else {
            label.setText("= 0%");
            label.setStyle("-fx-font-size: 11; -fx-text-fill: #94A3B8;"
                    + " -fx-padding: 2 6;");
        }
    }

    private void hover(HBox fila, String normal, String over) {
        fila.setOnMouseEntered(e ->
                fila.setStyle(fila.getStyle().replace(
                        "background-color: " + normal,
                        "background-color: " + over)));
        fila.setOnMouseExited(e ->
                fila.setStyle(fila.getStyle().replace(
                        "background-color: " + over,
                        "background-color: " + normal)));
    }

    private String[] estiloAlerta(String tipo) {
        return switch (tipo) {
            case "SIN_STOCK"                -> new String[]{"#DC2626","#FEF2F2","#FEE2E2"};
            case "STOCK_BAJO"               -> new String[]{"#EA580C","#FFF7ED","#FFEDD5"};
            case "PROXIMO_MINIMO"           -> new String[]{"#2563EB","#EFF6FF","#DBEAFE"};
            case "PRECIO_FUERA_RANGO_ALTA"  -> new String[]{"#EA580C","#FFF7ED","#FFEDD5"};
            case "PRECIO_FUERA_RANGO_MEDIA" -> new String[]{"#B45309","#FFFBEB","#FEF3C7"};
            case "CREDITO_VENCIMIENTO"      -> new String[]{"#DC2626","#FEF2F2","#FEE2E2"};
            case "CUENTA_POR_PAGAR"         -> new String[]{"#DC2626","#FEF2F2","#FEE2E2"};
            case "USUARIO_BLOQUEADO"        -> new String[]{"#2563EB","#EFF6FF","#DBEAFE"};
            default -> new String[]{"#64748B","#F8FAFC","#F1F5F9"};
        };
    }

    private double varVentas(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0)
            return 0;
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }

    private String truncar(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private void navegar(Runnable accion) {
        if (mainController != null) accion.run();
    }

    // ── Accesos rápidos ───────────────────────────────────────────────

    @FXML public void onAccesoVenta()      { navegar(mainController::onVentas); }
    @FXML public void onAccesoCompra()     { navegar(mainController::onCompras); }
    @FXML public void onAccesoCliente()    { navegar(mainController::onClientes); }
    @FXML public void onAccesoInventario() { navegar(mainController::onInventario); }
    @FXML public void onAccesoAlertas()    { navegar(mainController::onAlertas); }
    @FXML public void onAccesoVentas()     { navegar(mainController::onVentas); }
    @FXML public void onAccesoCreditos()   { navegar(mainController::onCreditos); }
}