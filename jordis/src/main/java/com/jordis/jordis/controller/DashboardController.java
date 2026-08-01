package com.jordis.jordis.controller;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.VentaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.service.DashboardService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
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
    @FXML private ComboBox<String> cmbTopProductos;
    @FXML private VBox  panelTopProductos;
    @FXML private VBox  panelMetodosPago;
    @FXML private VBox  panelDevolucionesResumen;

    // ── Pestaña Inventario ──
    @FXML private Label    lblTotalProductos;
    @FXML private Label    lblValorInventario;
    @FXML private Label    lblStockBajo;
    @FXML private Label    lblSinStock;
    @FXML private Label    lblStockNormal;
    @FXML private Label    lblPctNormal;
    @FXML private VBox     cardStockBajo;
    @FXML private VBox     cardSinStock;
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
    private final com.jordis.jordis.repository.DevolucionRepository devolucionRepository;
    private final com.jordis.jordis.repository.ClienteRepository clienteRepository;

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
        cmbMetrica.getItems().addAll(
                "Ventas (RD$)",
                "Cantidad de ventas",
                "Ticket promedio (RD$)",
                "Productos vendidos (u.)");
        cmbMetrica.setValue("Ventas (RD$)");

        cmbTopProductos.getItems().addAll("Top 3", "Top 5", "Top 10");
        cmbTopProductos.setValue("Top 5");

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

    @FXML
    public void onCambiarTopProductos() {
        LocalDateTime desde = dashboardService.getDesde(periodoActual);
        LocalDateTime hasta = dashboardService.getHasta();
        renderizarTopProductos(desde, hasta);
    }

    @FXML
    public void onVerActividad() {
        navegar(mainController::onAuditoria);
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
        BigDecimal totalVentas = dashboardService.getTotalVentas(desde, hasta);
        lblVentas.setText("RD$" + fmt(totalVentas));
        aplicarVariacion(lblVarVentas,
                dashboardService.getVariacionVentas(desde, hasta));

        long numVentas = dashboardService.getNumeroVentas(desde, hasta);
        lblTransacciones.setText(String.valueOf(numVentas));
        aplicarVariacion(lblVarTrans,
                dashboardService.getVariacionNumeroVentas(desde, hasta));
        BigDecimal ticket = dashboardService.getTicketPromedio(desde, hasta);
        lblTicketPromedio.setText("Ticket: RD$" + fmt(ticket));

        BigDecimal cred = dashboardService.getTotalCreditosPendientes();
        lblCreditosPendientes.setText("RD$" + fmt(cred));
        long venc  = dashboardService.getCreditosVencidos();
        long porVenc = dashboardService.getCreditosPorVencer();
        lblCreditosDetalle.setText(
                venc + " vencido(s) · " + porVenc + " por vencer");

        BigDecimal cpp = dashboardService.getTotalCuentasPorPagar();
        lblCuentasPorPagar.setText("RD$" + fmt(cpp));
        long cuentasVenc = dashboardService.getCuentasVencidas();
        lblCuentasDetalle.setText(
                cuentasVenc > 0
                        ? cuentasVenc + " cuenta(s) vencida(s)"
                        : "Sin cuentas vencidas");

        cardVentas.setOnMouseClicked(e -> navegar(mainController::onVentas));
        cardTransacciones.setOnMouseClicked(e -> navegar(mainController::onVentas));
        cardCreditos.setOnMouseClicked(e -> navegar(mainController::onCreditos));
        cardCuentasPagar.setOnMouseClicked(e -> navegar(mainController::onCuentasPorPagar));

        var alertas = dashboardService.getAlertasCriticas();
        long numAl  = dashboardService.getAlertasNoLeidas();
        lblNumAlertas.setText(numAl > 0 ? String.valueOf(numAl) : "");
        lblNumAlertas.setVisible(numAl > 0);

        panelAlertas.getChildren().clear();
        if (alertas.isEmpty()) {
            panelAlertas.getChildren().add(filaOk("Sin alertas pendientes"));
        } else {
            alertas.forEach(a ->
                    panelAlertas.getChildren().add(crearFilaAlerta(a)));
        }

        panelActividad.getChildren().clear();
        var actividad = dashboardService.getActividadReciente(5);
        if (actividad.isEmpty()) {
            panelActividad.getChildren().add(filaOk("Sin actividad reciente"));
        } else {
            actividad.forEach(a ->
                    panelActividad.getChildren().add(crearFilaActividad(a)));
        }
    }

    // ── VENTAS ────────────────────────────────────────────────────────

    private void cargarVentas(LocalDateTime desde, LocalDateTime hasta) {
        BigDecimal tv = dashboardService.getTotalVentas(desde, hasta);
        long nv = dashboardService.getNumeroVentas(desde, hasta);
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
                .mapToLong(VentaProducto::getCantidad).sum();
        mkLblProductos.setText(String.valueOf(unidVendidas));

        mkVentas.setOnMouseClicked(e -> navegar(mainController::onVentas));
        mkCantidad.setOnMouseClicked(e -> navegar(mainController::onVentas));

        actualizarGrafica(desde, hasta);
        renderizarTopProductos(desde, hasta);

        panelMetodosPago.getChildren().clear();
        Map<String, List<Venta>> porMetodo = ventaRepository
                .findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .collect(Collectors.groupingBy(Venta::getMetodoPago));

        String[] colMetodo = {"#2563EB", "#15803D", "#B45309", "#6D28D9"};
        long totalM = porMetodo.values().stream()
                .mapToLong(List::size).sum();

        if (porMetodo.isEmpty()) {
            panelMetodosPago.getChildren().add(
                    filaOk("Sin ventas en el período"));
        } else {
            List<PieChart.Data> datosDonut = new ArrayList<>();
            int ci = 0;
            for (var e : porMetodo.entrySet()) {
                BigDecimal montoM = e.getValue().stream()
                        .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                double pct = totalM > 0
                        ? (double) e.getValue().size() / totalM * 100 : 0;
                panelMetodosPago.getChildren().add(
                        crearFilaMetodo(e.getKey(), e.getValue().size(),
                                montoM, pct, colMetodo[ci % colMetodo.length]));
                datosDonut.add(new PieChart.Data(
                        com.jordis.jordis.util.TextoFormateador.humanizar(e.getKey()),
                        montoM.doubleValue()));
                ci++;
            }

            PieChart donut = new PieChart();
            donut.getData().addAll(datosDonut);
            donut.setLegendVisible(false);
            donut.setLabelsVisible(true);
            donut.setPrefSize(210, 190);
            donut.setMaxSize(210, 190);
            donut.setStyle("-fx-padding: 6 0 0 0;");
            javafx.application.Platform.runLater(() -> {
                int idx = 0;
                for (var d : donut.getData()) {
                    if (d.getNode() != null) {
                        d.getNode().setStyle(
                                "-fx-pie-color: " + colMetodo[idx % colMetodo.length] + ";");
                    }
                    idx++;
                }
            });
            panelMetodosPago.getChildren().add(donut);
        }

        panelDevolucionesResumen.getChildren().clear();
        var devolucionesPeriodo = devolucionRepository.findEntreFechas(desde, hasta).stream()
                .filter(d -> d.getEstado() == EstadoDevolucion.REGISTRADA)
                .toList();

        if (devolucionesPeriodo.isEmpty()) {
            panelDevolucionesResumen.getChildren().add(
                    filaOk("Sin devoluciones en el período"));
        } else {
            BigDecimal montoDevuelto = devolucionesPeriodo.stream()
                    .map(Devolucion::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            long notaCredito = devolucionesPeriodo.stream()
                    .filter(d -> d.getTipoDevolucion() == TipoDevolucion.NOTA_CREDITO).count();
            long saldoFavor = devolucionesPeriodo.size() - notaCredito;

            long clientesConSaldo = clienteRepository.contarConSaldoAFavor();
            BigDecimal saldoPendienteTotal = clienteRepository.sumaSaldoAFavorPendiente();

            panelDevolucionesResumen.getChildren().addAll(
                    crearFilaResumen("Devoluciones registradas", String.valueOf(devolucionesPeriodo.size())),
                    crearFilaResumen("Clientes con saldo pendiente", String.valueOf(clientesConSaldo)),
                    crearFilaResumen("Saldo a favor pendiente (total)", "RD$" + fmt(saldoPendienteTotal))
            );
        }
    }

    private int limiteTopProductos() {
        String v = cmbTopProductos.getValue();
        if (v == null) return 5;
        return switch (v) {
            case "Top 3"  -> 3;
            case "Top 10" -> 10;
            default       -> 5;
        };
    }

    @SuppressWarnings("unchecked")
    private void renderizarTopProductos(LocalDateTime desde, LocalDateTime hasta) {
        panelTopProductos.getChildren().clear();
        int limite = limiteTopProductos();
        var top = dashboardService.getTopProductos(desde, hasta, limite);

        if (top.isEmpty()) {
            panelTopProductos.getChildren().add(
                    filaOk("Sin ventas en el período"));
            return;
        }

        long totalUnidPeriodo = ventaRepository.findEntreFechas(desde, hasta)
                .stream().filter(v -> !v.getAnulada())
                .flatMap(v -> v.getDetalles().stream())
                .mapToLong(VentaProducto::getCantidad).sum();

        long maxU = (long) top.get(0).get("unidades");
        for (int i = 0; i < top.size(); i++) {
            var m = top.get(i);
            long unids     = (long) m.get("unidades");
            BigDecimal ing = (BigDecimal) m.get("ingresos");
            double pctUnid = totalUnidPeriodo > 0
                    ? (double) unids / totalUnidPeriodo * 100 : 0;
            panelTopProductos.getChildren().add(
                    crearFilaTopProducto(i + 1,
                            (String) m.get("nombre"),
                            unids, ing, pctUnid, maxU));
        }
    }

    private void actualizarGrafica(LocalDateTime desde, LocalDateTime hasta) {
        graficaVentas.getData().clear();

        Map<String, Double> serieDatos =
                dashboardService.getSeriePorPeriodo(periodoActual, metricaActual);

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName(cmbMetrica.getValue());

        String leyendaEje = switch (metricaActual) {
            case "CANTIDAD"  -> "Ventas";
            case "TICKET"    -> "RD$ promedio";
            case "PRODUCTOS" -> "Unidades";
            default          -> "RD$";
        };

        double totalGraf = 0;
        for (var e : serieDatos.entrySet()) {
            serie.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            totalGraf += e.getValue();
        }
        if ("TICKET".equals(metricaActual)) {
            totalGraf = dashboardService.getTicketPromedio(desde, hasta).doubleValue();
        }

        ejeY.setLabel(leyendaEje);
        graficaVentas.getData().add(serie);
        boolean esMoneda = metricaActual.equals("VENTAS") || metricaActual.equals("TICKET");
        lblTotalGrafica.setText("Total: " + (esMoneda
                ? "RD$" + fmt(BigDecimal.valueOf(totalGraf))
                : fmt(BigDecimal.valueOf(totalGraf))));

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

        cardStockBajo.setOnMouseClicked(e -> navegar(mainController::onInventario));
        cardSinStock.setOnMouseClicked(e -> navegar(mainController::onInventario));

        panelProductosCriticos.getChildren().clear();
        var criticos = todos.stream()
                .filter(p -> p.isStockBajo() || p.getStock() == 0)
                .sorted(Comparator.comparingInt(Producto::getStock))
                .limit(8).toList();

        if (criticos.isEmpty()) {
            lblTituloProductosCrit.setText("✅  Sin productos críticos");
            panelProductosCriticos.getChildren().add(
                    filaOk("Todo el inventario está en niveles saludables"));
        } else {
            lblTituloProductosCrit.setText("⚠  Productos críticos");
            criticos.forEach(p ->
                    panelProductosCriticos.getChildren().add(
                            crearFilaProductoCritico(p)));
        }

        panelCategorias.getChildren().clear();
        Map<String, Long> porCat = todos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategoria() != null
                                ? p.getCategoria().getNombre() : "Sin categoría",
                        Collectors.counting()));

        long maxCat = porCat.values().stream()
                .mapToLong(Long::longValue).max().orElse(1);

        for (var e : porCat.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(6).toList()) {
            double pct = (double) e.getValue() / total * 100;
            panelCategorias.getChildren().add(
                    crearFilaCategoria(e.getKey(), e.getValue(),
                            pct, e.getValue(), maxCat, "#2563EB"));
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
                navegar(mainController::onCreditosVencidosFiltrado));
        cardCreditosPorVencer.setOnMouseClicked(e ->
                navegar(mainController::onCreditos));

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
            lblTituloPanelDin.setText("📊  Resumen de clientes");

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
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 9 16; -fx-background-color: "
                + col[1] + "; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        Rectangle dot = new Rectangle(7, 7);
        dot.setFill(javafx.scene.paint.Color.web(col[0]));
        dot.setArcWidth(7); dot.setArcHeight(7);

        Label hora = new Label(a.getFechaHora()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        hora.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");

        VBox txt = new VBox(2);
        Label t = new Label(a.getTitulo());
        t.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: " + col[0] + ";");
        txt.getChildren().add(t);
        if (a.getDescripcion() != null && !a.getDescripcion().isBlank()) {
            Label d = new Label(a.getDescripcion());
            d.setStyle("-fx-font-size: 10; -fx-text-fill: #64748B;");
            d.setWrapText(true);
            d.setMaxWidth(Double.MAX_VALUE);
            txt.getChildren().add(d);
        }
        HBox.setHgrow(txt, Priority.ALWAYS);

        fila.getChildren().addAll(dot, txt, hora);
        fila.setOnMouseClicked(e -> navegar(mainController::onAlertas));
        hover(fila, col[1], col[2]);
        return fila;
    }

    private HBox crearFilaActividad(AuditoriaLog log) {
        String[] est = estiloActividad(log.getAccion());
        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 8 16; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        Label lIcono = new Label(est[0]);
        lIcono.setStyle("-fx-font-size: 14;");

        VBox info = new VBox(1);
        Label lTitulo = new Label(etiquetaActividad(log.getAccion()));
        lTitulo.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        String detalle = log.getDetalle() != null ? log.getDetalle() : "";
        Label lDet = new Label(detalle
                + (detalle.isEmpty() ? "" : "  ·  ")
                + (log.getUsuario() != null ? log.getUsuario().getNombre() : "Sistema")
                + "  ·  " + log.getFechaHora().format(FMT));
        lDet.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        lDet.setWrapText(true);
        lDet.setMaxWidth(Double.MAX_VALUE);
        info.getChildren().addAll(lTitulo, lDet);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lPunto = new Label("●");
        lPunto.setStyle("-fx-font-size: 8; -fx-text-fill: " + est[1] + ";");

        fila.getChildren().addAll(lIcono, info, lPunto);
        hover(fila, "white", "#F8FAFC");
        return fila;
    }

    private String[] estiloActividad(String accion) {
        return switch (accion) {
            case "VENTA_REGISTRADA"     -> new String[]{"🛒", "#2563EB"};
            case "VENTA_ANULADA"        -> new String[]{"❌", "#DC2626"};
            case "COMPRA_REGISTRADA"    -> new String[]{"🏭", "#6D28D9"};
            case "CLIENTE_CREADO"       -> new String[]{"👤", "#15803D"};
            case "PRODUCTO_CREADO"      -> new String[]{"📦", "#0891B2"};
            case "PROVEEDOR_CREADO"     -> new String[]{"🏢", "#0891B2"};
            case "USUARIO_CREADO"       -> new String[]{"🆕", "#15803D"};
            case "USUARIO_DESBLOQUEADO" -> new String[]{"🔓", "#2563EB"};
            case "PRECIO_MODIFICADO"    -> new String[]{"💲", "#B45309"};
            case "INVENTARIO_AJUSTADO"  -> new String[]{"🔧", "#B45309"};
            case "DEVOLUCION_REGISTRADA" -> new String[]{"↩", "#B45309"};
            case "CAJA_ABIERTA"         -> new String[]{"🔓", "#15803D"};
            case "CIERRE_CAJA"          -> new String[]{"🔒", "#64748B"};
            default -> new String[]{"•", "#64748B"};
        };
    }

    private String etiquetaActividad(String accion) {
        return com.jordis.jordis.util.TextoFormateador.etiquetaAccion(accion);
    }

    private HBox crearFilaTopProducto(int pos, String nombre, long unidades,
                                      BigDecimal ingresos, double pctUnidades,
                                      long maxU) {
        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);
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
        Pane barra = barraProgreso((double) unidades / Math.max(maxU, 1), "#2563EB");
        Label lIng = new Label("RD$" + fmt(ingresos) + " generados");
        lIng.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lNom, barra, lIng);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox der = new VBox(1);
        der.setAlignment(Pos.CENTER_RIGHT);
        Label lUnid = new Label(unidades + " u.");
        lUnid.setStyle("-fx-font-size: 15; -fx-font-weight: bold;"
                + " -fx-text-fill: #2563EB;");
        Label lPct = new Label(String.format("%.1f%% del total", pctUnidades));
        lPct.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        der.getChildren().addAll(lUnid, lPct);

        fila.getChildren().addAll(lPos, info, der);
        return fila;
    }

    private VBox crearFilaMetodo(String metodo, int count,
                                 BigDecimal monto, double pct, String color) {
        VBox box = new VBox(5);
        box.setStyle("-fx-padding: 6 0;");
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Region swatch = new Region();
        swatch.setPrefSize(10, 10);
        swatch.setMinSize(10, 10);
        swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
        Label lM = new Label(com.jordis.jordis.util.TextoFormateador.humanizar(metodo));
        lM.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lV = new Label("RD$" + fmt(monto)
                + "  (" + String.format("%.0f", pct) + "% de las ventas)");
        lV.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
        top.getChildren().addAll(swatch, lM, esp, lV);
        Pane barra = barraProgreso(pct / 100, color);
        box.getChildren().addAll(top, barra);
        return box;
    }

    private VBox crearFilaComparativa(String etiqueta,
                                      String actual, double variacion) {
        VBox fila = new VBox(3);
        fila.setStyle("-fx-padding: 8 0; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");

        HBox linea1 = new HBox(8);
        linea1.setAlignment(Pos.CENTER_LEFT);
        Label lEt = new Label(etiqueta);
        lEt.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lAct = new Label(actual);
        lAct.setStyle("-fx-font-size: 13; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        linea1.getChildren().addAll(lEt, esp, lAct);

        HBox linea2 = new HBox();
        linea2.setAlignment(Pos.CENTER_RIGHT);
        Label lVar = new Label();
        aplicarVariacion(lVar, variacion);
        linea2.getChildren().add(lVar);

        fila.getChildren().addAll(linea1, linea2);
        return fila;
    }

    private HBox crearFilaProductoCritico(Producto p) {
        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);
        String fondo = p.getStock() == 0 ? "#FEF2F2" : "#FFFBEB";
        fila.setStyle("-fx-padding: 9 16; -fx-background-color: " + fondo
                + "; -fx-border-color: #F1F5F9; -fx-border-width: 0 0 1 0;"
                + " -fx-cursor: hand;");

        VBox info = new VBox(2);
        Label lNom = new Label(p.getNombre());
        lNom.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        Label lMin = new Label("Mínimo: " + p.getStockMinimo() + " u.");
        lMin.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lNom, lMin);
        HBox.setHgrow(info, Priority.ALWAYS);

        String color = p.getStock() == 0 ? "#DC2626" : "#B45309";
        Label lEstado = new Label(p.getStock() == 0
                ? "Sin stock" : "Quedan " + p.getStock() + " u.");
        lEstado.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: " + color + ";");

        fila.getChildren().addAll(info, lEstado);
        fila.setOnMouseClicked(e ->
                navegar(() -> mainController.onInventarioFiltrado(p.getIdProducto())));
        hover(fila, fondo, p.getStock() == 0 ? "#FEE2E2" : "#FEF3C7");
        return fila;
    }

    private VBox crearFilaCategoria(String nombre, long count,
                                    double pct, long val, long maxVal,
                                    String color) {
        VBox box = new VBox(4);
        box.setStyle("-fx-padding: 6 0;");
        HBox info = new HBox();
        info.setAlignment(Pos.CENTER_LEFT);
        Label lNom = new Label(nombre);
        lNom.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lCnt = new Label(count + " prod. · "
                + String.format("%.0f%%", pct));
        lCnt.setStyle("-fx-font-size: 11; -fx-text-fill: #94A3B8;");
        info.getChildren().addAll(lNom, esp, lCnt);
        Pane barra = barraProgreso((double) val / Math.max(maxVal, 1), color);
        box.getChildren().addAll(info, barra);
        return box;
    }

    private HBox crearFilaTopCliente(int pos, String nombre,
                                     BigDecimal total, double pct,
                                     BigDecimal maxTotal) {
        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);
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
        Pane barra = barraProgreso(ratio, "#2563EB");
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
        fila.setAlignment(Pos.CENTER_LEFT);
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
        fila.setOnMouseClicked(e ->
                navegar(() -> mainController.onCreditosFiltrado(v.getIdVenta())));
        hover(fila, "#FEF2F2", "#FEE2E2");
        return fila;
    }

    // ── Utilidades de UI ──────────────────────────────────────────────

    private Pane barraProgreso(double ratio, String color) {
        double r = Math.max(0, Math.min(ratio, 1));

        Region fondo = new Region();
        fondo.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 4;");

        Region prog = new Region();
        prog.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");

        Pane contenedor = new Pane(fondo, prog);
        contenedor.setMinHeight(7);
        contenedor.setPrefHeight(7);
        contenedor.setMaxHeight(7);
        contenedor.setMaxWidth(Double.MAX_VALUE);

        contenedor.widthProperty().addListener((obs, old, nuevo) -> {
            double w = nuevo.doubleValue();
            fondo.resizeRelocate(0, 0, w, 7);
            prog.resizeRelocate(0, 0, w * r, 7);
        });

        HBox.setHgrow(contenedor, Priority.ALWAYS);
        return contenedor;
    }

    private HBox crearFilaResumen(String etiqueta, String valor) {
        HBox fila = new HBox();
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 7 0; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;");
        Label lEt = new Label(etiqueta);
        lEt.setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label lVal = new Label(valor);
        lVal.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        fila.getChildren().addAll(lEt, esp, lVal);
        return fila;
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
        h.setAlignment(Pos.CENTER_LEFT);
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
        String frase = switch (periodoActual) {
            case "HOY"    -> "que ayer";
            case "SEMANA" -> "que la semana pasada";
            case "MES"    -> "que el mes pasado";
            default       -> "que el año pasado";
        };
        if (variacion > 0) {
            label.setText("▲ +" + String.format("%.1f", variacion) + "% " + frase);
            label.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                    + " -fx-text-fill: #15803D; -fx-background-color: #F0FDF4;"
                    + " -fx-background-radius: 5; -fx-padding: 2 6;");
        } else if (variacion < 0) {
            label.setText("▼ " + String.format("%.1f", variacion) + "% " + frase);
            label.setStyle("-fx-font-size: 11; -fx-font-weight: bold;"
                    + " -fx-text-fill: #DC2626; -fx-background-color: #FEF2F2;"
                    + " -fx-background-radius: 5; -fx-padding: 2 6;");
        } else {
            label.setText("= igual " + frase);
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