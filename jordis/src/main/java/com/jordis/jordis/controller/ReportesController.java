package com.jordis.jordis.controller;

import com.jordis.jordis.model.*;
import com.jordis.jordis.service.FacturaService;
import com.jordis.jordis.service.ReporteService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SearchableComboBox;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportesController {

    @FXML private VBox   panelTipos;
    @FXML private VBox   panelFiltros;
    @FXML private Button btnGenerar;
    @FXML private Button btnPDF;
    @FXML private Label  lblEstado;

    @FXML private VBox   panelVacio;
    @FXML private VBox   panelContenido;
    @FXML private Label  lblTituloReporte;
    @FXML private Label  lblConteo;
    @FXML private HBox   panelKpis;
    @FXML private TableView<Object> tablaPrevia;

    private final ReporteService reporteService;
    private final FacturaService facturaService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Estado
    private String     tipoActual;
    private String     rutaUltimoPdf;
    private Object     datosActuales;

    // Filtros comunes
    private DatePicker dpDesde;
    private DatePicker dpHasta;

    // Filtros específicos
    private SearchableComboBox<Cliente> cmbCliente;
    private SearchableComboBox<Usuario>   cmbCajero;
    private SearchableComboBox<String>    cmbMetodoPago;
    private SearchableComboBox<Categoria> cmbCategoria;
    private SearchableComboBox<String>    cmbMarca;
    private SearchableComboBox<Proveedor> cmbProveedor;

    // ── Tipos de reporte disponibles ─────────────────────────────────

    private static final Object[][] TIPOS = {
            {"VENTAS",     "💰", "Ventas",
                    "Por período, cliente y método de pago"},
            {"PRODUCTOS",  "📦", "Productos vendidos",
                    "Ranking y análisis por período"},
            {"COMPRAS",    "🛒", "Compras",
                    "Historial de compras a proveedores"},
            {"CREDITOS",   "📋", "Créditos",
                    "Créditos otorgados, pendientes y vencidos"},
            {"UTILIDADES", "📈", "Utilidades",
                    "Ganancia bruta y margen por período"}
    };

    @FXML
    public void initialize() {
        construirTipos();
        // Inicializar filtros de fechas por defecto
        dpDesde = crearDatePicker(
                LocalDate.now().withDayOfMonth(1));
        dpHasta = crearDatePicker(LocalDate.now());
    }

    // ── Construcción del panel de tipos ──────────────────────────────

    private void construirTipos() {
        panelTipos.getChildren().clear();
        for (Object[] tipo : TIPOS) {
            panelTipos.getChildren().add(
                    crearTarjetaTipo(
                            (String) tipo[0], (String) tipo[1],
                            (String) tipo[2], (String) tipo[3]));
        }
    }

    private HBox crearTarjetaTipo(String id, String icono,
                                  String nombre, String desc) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-padding: 10 14; -fx-background-radius: 8;"
                + " -fx-background-color: transparent; -fx-cursor: hand;");

        Label lIcono = new Label(icono);
        lIcono.setStyle("-fx-font-size: 18; -fx-background-color: #EFF6FF;"
                + " -fx-background-radius: 8; -fx-padding: 5 7;"
                + " -fx-min-width: 36; -fx-alignment: CENTER;");

        VBox txt = new VBox(2);
        Label lNom = new Label(nombre);
        lNom.setStyle("-fx-font-size: 13; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        Label lDesc = new Label(desc);
        lDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #94A3B8;");
        txt.getChildren().addAll(lNom, lDesc);

        card.getChildren().addAll(lIcono, txt);

        // Interacciones
        card.setOnMouseEntered(e ->
                card.setStyle("-fx-padding: 10 14; -fx-background-radius: 8;"
                        + " -fx-background-color: #EFF6FF; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> {
            if (!id.equals(tipoActual)) {
                card.setStyle("-fx-padding: 10 14; -fx-background-radius: 8;"
                        + " -fx-background-color: transparent; -fx-cursor: hand;");
            }
        });
        card.setOnMouseClicked(e -> seleccionarTipo(id, card));

        return card;
    }

    private void seleccionarTipo(String id, HBox card) {
        tipoActual = id;
        rutaUltimoPdf = null;
        btnPDF.setDisable(true);
        datosActuales = null;

        cmbCliente    = null;
        cmbCajero     = null;
        cmbMetodoPago = null;
        cmbCategoria  = null;
        cmbMarca      = null;
        cmbProveedor  = null;

        // Resaltar tarjeta seleccionada
        panelTipos.getChildren().forEach(n -> {
            if (n instanceof HBox h) {
                h.setStyle("-fx-padding: 10 14; -fx-background-radius: 8;"
                        + " -fx-background-color: transparent; -fx-cursor: hand;");
            }
        });
        card.setStyle("-fx-padding: 10 14; -fx-background-radius: 8;"
                + " -fx-background-color: #EFF6FF; -fx-cursor: hand;"
                + " -fx-border-color: #BFDBFE; -fx-border-width: 0 0 0 3;"
                + " -fx-border-radius: 0 8 8 0;");

        construirFiltros(id);
        ocultarContenido();
        lblEstado.setText("");
    }

    // ── Filtros dinámicos por tipo ────────────────────────────────────

    private void construirFiltros(String tipo) {
        panelFiltros.getChildren().clear();

        // Fechas — siempre presentes
        agregarLabel("Fecha desde:");
        dpDesde = crearDatePicker(LocalDate.now().withDayOfMonth(1));
        panelFiltros.getChildren().add(dpDesde);

        agregarLabel("Fecha hasta:");
        dpHasta = crearDatePicker(LocalDate.now());
        panelFiltros.getChildren().add(dpHasta);

        switch (tipo) {
            case "VENTAS" -> {
                cmbCliente = new SearchableComboBox<>();
                cmbCliente.setPromptText("Todos los clientes");
                cmbCliente.setConverter(strConverter(
                        c -> c == null ? "Todos los clientes"
                                : c.getNombreCompleto()));
                List<Cliente> todosCliente = new java.util.ArrayList<>();
                todosCliente.add(null);
                todosCliente.addAll(reporteService.obtenerClientes());
                cmbCliente.getItems().addAll(todosCliente);
                estiloCombo(cmbCliente);
                panelFiltros.getChildren().add(cmbCliente);

                agregarLabel("Cajero (opcional):");
                cmbCajero = new SearchableComboBox<>();
                cmbCajero.setPromptText("Todos los cajeros");
                cmbCajero.setConverter(strConverter(
                        u -> u == null ? "Todos"
                                : u.getNombreCompleto()));
                List<Usuario> todosCajero = new java.util.ArrayList<>();
                todosCajero.add(null);
                todosCajero.addAll(reporteService.obtenerCajeros());
                cmbCajero.getItems().addAll(todosCajero);
                estiloCombo(cmbCajero);
                panelFiltros.getChildren().add(cmbCajero);

                agregarLabel("Método de pago (opcional):");
                cmbMetodoPago = new SearchableComboBox<>();
                cmbMetodoPago.setPromptText("Todos");
                cmbMetodoPago.getItems().addAll(
                        null, "EFECTIVO", "TARJETA",
                        "TRANSFERENCIA", "CREDITO");
                estiloCombo(cmbMetodoPago);
                panelFiltros.getChildren().add(cmbMetodoPago);
            }
            case "PRODUCTOS" -> {
                agregarLabel("Categoría (opcional):");
                cmbCategoria = new SearchableComboBox<>();
                cmbCategoria.setPromptText("Todas las categorías");
                cmbCategoria.setConverter(strConverter(
                        c -> c == null ? "Todas" : c.getNombre()));
                List<Categoria> todasCategoria = new java.util.ArrayList<>();
                todasCategoria.add(null);
                todasCategoria.addAll(reporteService.obtenerCategorias());
                cmbCategoria.getItems().addAll(todasCategoria);
                estiloCombo(cmbCategoria);
                panelFiltros.getChildren().add(cmbCategoria);

                agregarLabel("Marca (opcional):");
                cmbMarca = new SearchableComboBox<>();
                cmbMarca.setPromptText("Todas las marcas");
                cmbMarca.setConverter(strConverter(
                        m -> m == null ? "Todas" : m));
                List<String> todasMarca = new java.util.ArrayList<>();
                todasMarca.add(null);
                todasMarca.addAll(reporteService.obtenerMarcas());
                cmbMarca.getItems().addAll(todasMarca);
                estiloCombo(cmbMarca);
                panelFiltros.getChildren().add(cmbMarca);
            }
            case "COMPRAS" -> {
                agregarLabel("Proveedor (opcional):");
                cmbProveedor = new SearchableComboBox<>();
                cmbProveedor.setPromptText("Todos los proveedores");
                cmbProveedor.setConverter(strConverter(
                        p -> p == null ? "Todos" : p.getNombre()));
                List<Proveedor> todosProveedor = new java.util.ArrayList<>();
                todosProveedor.add(null);
                todosProveedor.addAll(reporteService.obtenerProveedores());
                cmbProveedor.getItems().addAll(todosProveedor);
                estiloCombo(cmbProveedor);
                panelFiltros.getChildren().add(cmbProveedor);
            }
            // CREDITOS y UTILIDADES — solo fechas
        }
    }

    private void agregarLabel(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
        panelFiltros.getChildren().add(l);
    }

    // ── Generar reporte ───────────────────────────────────────────────

    @FXML
    public void onGenerar() {
        if (tipoActual == null) {
            lblEstado.setText("Selecciona un tipo de reporte.");
            return;
        }
        if (dpDesde.getValue() == null || dpHasta.getValue() == null) {
            lblEstado.setText("Selecciona el rango de fechas.");
            return;
        }
        if (dpDesde.getValue().isAfter(dpHasta.getValue())) {
            lblEstado.setText("La fecha inicial debe ser anterior a la final.");
            return;
        }

        LocalDateTime desde = dpDesde.getValue().atStartOfDay();
        LocalDateTime hasta = dpHasta.getValue().atTime(23, 59, 59);
        lblEstado.setText("Generando...");
        rutaUltimoPdf = null;
        btnPDF.setDisable(true);

        try {
            switch (tipoActual) {
                case "VENTAS"     -> generarVentas(desde, hasta);
                case "PRODUCTOS"  -> generarProductos(desde, hasta);
                case "COMPRAS"    -> generarCompras(desde, hasta);
                case "CREDITOS"   -> generarCreditos(desde, hasta);
                case "UTILIDADES" -> generarUtilidades(desde, hasta);
            }
            mostrarContenido();
            lblEstado.setText("✓ Reporte generado correctamente.");
            btnPDF.setDisable(false);
        } catch (Exception e) {
            lblEstado.setText("Error: " + e.getMessage());
            log.error("Error generando reporte", e);
        }
    }

    // ── Ventas ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void generarVentas(LocalDateTime desde, LocalDateTime hasta) {
        Integer idCli = cmbCliente != null && cmbCliente.getValue() != null
                ? cmbCliente.getValue().getIdCliente() : null;
        Integer idCaj = cmbCajero != null && cmbCajero.getValue() != null
                ? cmbCajero.getValue().getIdUsuario() : null;
        String metodo = cmbMetodoPago != null
                ? cmbMetodoPago.getValue() : null;

        List<Venta> ventas =
                reporteService.obtenerVentas(desde, hasta,
                        idCli, idCaj, metodo);
        datosActuales = ventas;

        BigDecimal total = ventas.stream()
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ticket = ventas.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(ventas.size()),
                2, RoundingMode.HALF_UP);

        lblTituloReporte.setText("💰  Reporte de Ventas");
        lblConteo.setText(ventas.size() + " registros");

        kpis(new String[][]{
                {"TOTAL VENDIDO",   "RD$" + fmt(total),   "#2563EB"},
                {"VENTAS",          String.valueOf(ventas.size()), "#15803D"},
                {"TICKET PROMEDIO", "RD$" + fmt(ticket),  "#B45309"},
                {"A CRÉDITO",       String.valueOf(ventas.stream()
                        .filter(Venta::getEsCredito).count()), "#6D28D9"}
        });

        tablaPrevia.getColumns().clear();
        tablaPrevia.getColumns().addAll(
                col("Factura",  o -> nvl(((Venta)o).getNumeroFactura()), 90),
                col("Fecha",    o -> ((Venta)o).getFechaHora()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")), 110),
                col("Cliente",  o -> ((Venta)o).getCliente() != null
                        ? ((Venta)o).getCliente().getNombreCompleto() : "Ocasional", 150),
                col("Productos",o -> ((Venta)o).getDetalles().stream()
                        .map(vp -> vp.getProducto().getNombre()
                                + " x" + vp.getCantidad())
                        .limit(2).reduce((a,b) -> a+", "+b).orElse("—")
                        + (((Venta)o).getDetalles().size() > 2
                        ? " +más" : ""), 200),
                col("Total",    o -> "RD$" + fmt(((Venta)o).getTotal()), 100),
                col("Desc.",    o -> ((Venta)o).getDescuentoPorcentual()
                        .compareTo(BigDecimal.ZERO) > 0
                        ? ((Venta)o).getDescuentoPorcentual() + "%" : "—", 60),
                col("Pago",     o -> ((Venta)o).getMetodoPago(), 90)
        );
        tablaPrevia.setItems(
                FXCollections.observableArrayList(ventas));
    }

    // ── Productos vendidos ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void generarProductos(LocalDateTime desde, LocalDateTime hasta) {
        Integer idCat = cmbCategoria != null
                && cmbCategoria.getValue() != null
                ? cmbCategoria.getValue().getIdCategoria() : null;
        String marca = cmbMarca != null ? cmbMarca.getValue() : null;

        List<Map<String, Object>> datos =
                reporteService.obtenerProductosVendidos(desde, hasta,
                        idCat, marca);
        datosActuales = datos;

        long totalU = datos.stream()
                .mapToLong(m -> (long) m.get("unidades")).sum();
        BigDecimal totalI = datos.stream()
                .map(m -> (BigDecimal) m.get("ingresos"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTituloReporte.setText("📦  Productos Vendidos");
        lblConteo.setText(datos.size() + " productos");

        kpis(new String[][]{
                {"PRODUCTOS",        String.valueOf(datos.size()), "#2563EB"},
                {"UNIDADES VENDIDAS",String.valueOf(totalU),       "#15803D"},
                {"INGRESOS",         "RD$" + fmt(totalI),          "#B45309"}
        });

        tablaPrevia.getColumns().clear();
        tablaPrevia.getColumns().addAll(
                col("#",           o -> String.valueOf(
                        ((List<Map<String,Object>>)(Object)
                                tablaPrevia.getItems()).indexOf(o) + 1), 40),
                col("Producto",    o -> (String)((Map<?,?>)o).get("nombre"), 180),
                col("Marca",       o -> (String)((Map<?,?>)o).get("marca"), 100),
                col("Categoría",   o -> (String)((Map<?,?>)o).get("categoria"), 110),
                col("Unidades",    o -> String.valueOf(
                        ((Map<?,?>)o).get("unidades")), 80),
                col("Ingresos",    o -> "RD$" + fmt(
                        (BigDecimal)((Map<?,?>)o).get("ingresos")), 110)
        );
        tablaPrevia.setItems(
                FXCollections.observableArrayList(datos));
    }

    // ── Compras ───────────────────────────────────────────────────────

    private void generarCompras(LocalDateTime desde, LocalDateTime hasta) {
        Integer idProv = cmbProveedor != null
                && cmbProveedor.getValue() != null
                ? cmbProveedor.getValue().getIdProveedor() : null;

        List<Compra> compras =
                reporteService.obtenerCompras(desde, hasta, idProv);
        datosActuales = compras;

        BigDecimal total = compras.stream()
                .map(Compra::getTotalCompra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTituloReporte.setText("🛒  Reporte de Compras");
        lblConteo.setText(compras.size() + " compras");

        kpis(new String[][]{
                {"TOTAL COMPRAS",  String.valueOf(compras.size()), "#2563EB"},
                {"MONTO TOTAL",    "RD$" + fmt(total),             "#DC2626"},
                {"RECIBIDAS",      String.valueOf(compras.stream()
                        .filter(c -> "RECIBIDA".equals(c.getEstado()))
                        .count()), "#15803D"},
                {"PENDIENTES",     String.valueOf(compras.stream()
                        .filter(c -> "PENDIENTE".equals(c.getEstado()))
                        .count()), "#B45309"}
        });

        tablaPrevia.getColumns().clear();
        tablaPrevia.getColumns().addAll(
                col("#",           o -> String.valueOf(
                        ((Compra)o).getIdCompra()), 50),
                col("Fecha",       o -> ((Compra)o).getFechaPedido()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yy")), 80),
                col("Proveedor",   o -> ((Compra)o).getProveedor()
                        .getNombre(), 140),
                col("Productos",   o -> ((Compra)o).getDetalles().stream()
                        .map(d -> d.getProducto().getNombre()
                                + " x" + d.getCantidad())
                        .limit(2).reduce((a,b) -> a+", "+b).orElse("—"), 200),
                col("Total",       o -> "RD$" + fmt(
                        ((Compra)o).getTotalCompra()), 100),
                col("Estado",      o -> ((Compra)o).getEstado(), 90)
        );
        tablaPrevia.setItems(
                FXCollections.observableArrayList(compras));
    }

    // ── Créditos ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void generarCreditos(LocalDateTime desde, LocalDateTime hasta) {
        Map<String, Object> datos =
                reporteService.obtenerDatosCreditos(desde, hasta);
        datosActuales = datos;

        List<Venta> enPeriodo = (List<Venta>) datos.get("enPeriodo");

        lblTituloReporte.setText("📋  Reporte de Créditos");
        lblConteo.setText(enPeriodo.size() + " créditos en el período");

        kpis(new String[][]{
                {"OTORGADO",   "RD$" + fmt((BigDecimal) datos.get("totalOtorgado")), "#2563EB"},
                {"PENDIENTE",  "RD$" + fmt((BigDecimal) datos.get("totalPendiente")), "#B45309"},
                {"VENCIDO",    "RD$" + fmt((BigDecimal) datos.get("totalVencido")), "#DC2626"},
                {"PAGADOS",    String.valueOf(
                        ((List<?>)datos.get("pagados")).size()), "#15803D"}
        });

        tablaPrevia.getColumns().clear();
        tablaPrevia.getColumns().addAll(
                col("Factura",   o -> nvl(((Venta)o).getNumeroFactura()), 90),
                col("Fecha",     o -> ((Venta)o).getFechaHora()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yy")), 80),
                col("Cliente",   o -> ((Venta)o).getCliente() != null
                        ? ((Venta)o).getCliente().getNombreCompleto() : "—", 150),
                col("Total",     o -> "RD$" + fmt(((Venta)o).getTotal()), 100),
                col("Pagado",    o -> "RD$" + fmt(((Venta)o).getTotalPagado()), 100),
                col("Saldo",     o -> "RD$" + fmt(((Venta)o).getSaldoPendiente()), 100),
                col("Vence",     o -> ((Venta)o).getFechaLimiteCredito() != null
                        ? ((Venta)o).getFechaLimiteCredito()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yy")) : "—", 80),
                col("Estado",    o -> ((Venta)o).estaCancelado() ? "Pagado"
                        : ((Venta)o).getFechaLimiteCredito() != null
                        && ((Venta)o).getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now())
                        ? "Vencido" : "Pendiente", 80)
        );

        // Mostrar todos: en período + pendientes adicionales
        List<Venta> todos = ((List<Venta>)datos.get("pendientes"));
        tablaPrevia.setItems(
                FXCollections.observableArrayList(enPeriodo));
    }

    // ── Utilidades ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void generarUtilidades(LocalDateTime desde, LocalDateTime hasta) {
        Map<String, Object> datos =
                reporteService.obtenerUtilidades(desde, hasta);
        datosActuales = datos;

        List<Venta> ventas = (List<Venta>) datos.get("ventas");
        BigDecimal tv  = (BigDecimal) datos.get("totalVendido");
        BigDecimal cv  = (BigDecimal) datos.get("costoVentas");
        BigDecimal gan = (BigDecimal) datos.get("ganancia");
        BigDecimal mar = (BigDecimal) datos.get("margen");

        lblTituloReporte.setText("📈  Reporte de Utilidades");
        lblConteo.setText(ventas.size() + " ventas analizadas");

        kpis(new String[][]{
                {"VENTAS TOTALES",  "RD$" + fmt(tv),  "#2563EB"},
                {"COSTO ESTIMADO",  "RD$" + fmt(cv),  "#DC2626"},
                {"GANANCIA BRUTA",  "RD$" + fmt(gan), "#15803D"},
                {"MARGEN",          mar.setScale(1,
                        RoundingMode.HALF_UP) + "%",       "#B45309"}
        });

        tablaPrevia.getColumns().clear();
        tablaPrevia.getColumns().addAll(
                col("Factura",    o -> nvl(((Venta)o).getNumeroFactura()), 90),
                col("Fecha",      o -> ((Venta)o).getFechaHora()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yy")), 80),
                col("Cliente",    o -> ((Venta)o).getCliente() != null
                        ? ((Venta)o).getCliente().getNombreCompleto()
                        : "Ocasional", 150),
                col("Venta",      o -> "RD$" + fmt(((Venta)o).getTotal()), 100),
                col("Costo est.", o -> {
                    BigDecimal c = ((Venta)o).getDetalles().stream()
                            .map(vp -> {
                                BigDecimal p = vp.getProducto()
                                        .getUltimoPrecioCompra();
                                if (p == null) p = vp.getPrecioUnitario()
                                        .multiply(new BigDecimal("0.70"));
                                return p.multiply(
                                        BigDecimal.valueOf(vp.getCantidad()));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return "RD$" + fmt(c);
                }, 100),
                col("Ganancia",   o -> {
                    BigDecimal c = ((Venta)o).getDetalles().stream()
                            .map(vp -> {
                                BigDecimal p = vp.getProducto()
                                        .getUltimoPrecioCompra();
                                if (p == null) p = vp.getPrecioUnitario()
                                        .multiply(new BigDecimal("0.70"));
                                return p.multiply(
                                        BigDecimal.valueOf(vp.getCantidad()));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return "RD$" + fmt(((Venta)o).getTotal().subtract(c));
                }, 100)
        );
        tablaPrevia.setItems(
                FXCollections.observableArrayList(ventas));
    }

    // ── Exportar PDF ──────────────────────────────────────────────────

    @FXML
    @SuppressWarnings("unchecked")
    public void onExportarPDF() {
        if (datosActuales == null || tipoActual == null) return;

        LocalDateTime desde = dpDesde.getValue().atStartOfDay();
        LocalDateTime hasta = dpHasta.getValue().atTime(23, 59, 59);
        String filtrosTxt = construirTextoFiltros();

        try {
            lblEstado.setText("Generando PDF...");
            String ruta = switch (tipoActual) {
                case "VENTAS"     -> reporteService.generarPdfVentas(
                        (List<Venta>) datosActuales,
                        desde, hasta, filtrosTxt);
                case "PRODUCTOS"  -> reporteService.generarPdfProductos(
                        (List<Map<String, Object>>) datosActuales,
                        desde, hasta, filtrosTxt);
                case "COMPRAS"    -> reporteService.generarPdfCompras(
                        (List<Compra>) datosActuales,
                        desde, hasta, filtrosTxt);
                case "CREDITOS"   -> reporteService.generarPdfCreditos(
                        (Map<String, Object>) datosActuales,
                        desde, hasta);
                case "UTILIDADES" -> reporteService.generarPdfUtilidades(
                        (Map<String, Object>) datosActuales,
                        desde, hasta);
                default -> throw new RuntimeException("Tipo desconocido");
            };
            rutaUltimoPdf = ruta;
            facturaService.abrirPDF(ruta);
            lblEstado.setText("✓ PDF generado y abierto.");
        } catch (Exception e) {
            lblEstado.setText("Error: " + e.getMessage());
            log.error("Error generando PDF", e);
        }
    }

    private String construirTextoFiltros() {
        StringBuilder sb = new StringBuilder();
        if (cmbCliente != null && cmbCliente.getValue() != null)
            sb.append("Cliente: ")
                    .append(cmbCliente.getValue().getNombreCompleto())
                    .append("  ");
        if (cmbCajero != null && cmbCajero.getValue() != null)
            sb.append("Cajero: ")
                    .append(cmbCajero.getValue().getNombreCompleto())
                    .append("  ");
        if (cmbMetodoPago != null && cmbMetodoPago.getValue() != null)
            sb.append("Método: ").append(cmbMetodoPago.getValue()).append("  ");
        if (cmbCategoria != null && cmbCategoria.getValue() != null)
            sb.append("Categoría: ")
                    .append(cmbCategoria.getValue().getNombre()).append("  ");
        if (cmbMarca != null && cmbMarca.getValue() != null)
            sb.append("Marca: ").append(cmbMarca.getValue()).append("  ");
        if (cmbProveedor != null && cmbProveedor.getValue() != null)
            sb.append("Proveedor: ")
                    .append(cmbProveedor.getValue().getNombre()).append("  ");
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    // ── UI helpers ────────────────────────────────────────────────────

    private void mostrarContenido() {
        panelVacio.setVisible(false);
        panelVacio.setManaged(false);
        panelContenido.setVisible(true);
        panelContenido.setManaged(true);
    }

    private void ocultarContenido() {
        panelVacio.setVisible(true);
        panelVacio.setManaged(true);
        panelContenido.setVisible(false);
        panelContenido.setManaged(false);
    }

    private void kpis(String[][] datos) {
        panelKpis.getChildren().clear();
        for (String[] d : datos) {
            VBox card = new VBox(3);
            card.setStyle("-fx-padding: 12 20; -fx-border-color: #E2E8F0;"
                    + " -fx-border-width: 0 1 0 0;");
            HBox.setHgrow(card, Priority.ALWAYS);

            Label lLbl = new Label(d[0]);
            lLbl.setStyle("-fx-font-size: 9; -fx-font-weight: bold;"
                    + " -fx-text-fill: #94A3B8;");
            Label lVal = new Label(d[1]);
            lVal.setStyle("-fx-font-size: 18; -fx-font-weight: bold;"
                    + " -fx-text-fill: " + d[2] + ";");
            card.getChildren().addAll(lLbl, lVal);
            panelKpis.getChildren().add(card);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Object, String> col(String titulo,
                                                java.util.function.Function<Object, String> extractor,
                                                double ancho) {
        TableColumn<Object, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d ->
                new SimpleStringProperty(extractor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    private DatePicker crearDatePicker(LocalDate valor) {
        DatePicker dp = new DatePicker(valor);
        dp.setPrefWidth(268);
        dp.setStyle("-fx-font-size: 12;");
        return dp;
    }

    private <T> void estiloCombo(ComboBox<T> combo) {
        combo.setPrefWidth(268);
        combo.setPrefHeight(34);
        combo.setStyle("-fx-font-size: 12;");
    }

    private <T> javafx.util.StringConverter<T> strConverter(
            java.util.function.Function<T, String> fn) {
        return new javafx.util.StringConverter<>() {
            @Override public String toString(T t)        { return fn.apply(t); }
            @Override public T      fromString(String s) { return null; }
        };
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }

    private String nvl(String s) { return s != null ? s : "—"; }
}