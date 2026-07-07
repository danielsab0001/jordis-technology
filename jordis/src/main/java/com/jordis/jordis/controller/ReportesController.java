package com.jordis.jordis.controller;

import com.jordis.jordis.model.CuentaPorPagar;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.FacturaService;
import com.jordis.jordis.service.ReporteService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportesController {

    @FXML
    private RadioButton rbVentas;

    @FXML
    private RadioButton rbInventario;

    @FXML
    private RadioButton rbCobrar;

    @FXML
    private RadioButton rbPagar;

    @FXML
    private VBox panelFechas;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private TableView<Object> tablaReporte;

    @FXML
    private Label lblTituloReporte;

    @FXML
    private Label lblConteo;

    @FXML
    private Label lblEstado;

    private final ToggleGroup grupoReporte = new ToggleGroup();

    private final ReporteService reporteService;
    private final FacturaService facturaService;

    private List<Venta> ventasActuales;
    private List<Producto> inventarioActual;
    private List<Venta> cobrarActuales;
    private List<CuentaPorPagar> pagarActuales;

    private String tipoActual;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {

        rbVentas.setToggleGroup(grupoReporte);
        rbInventario.setToggleGroup(grupoReporte);
        rbCobrar.setToggleGroup(grupoReporte);
        rbPagar.setToggleGroup(grupoReporte);

        rbVentas.setSelected(true);

        dpDesde.setValue(LocalDate.now().withDayOfMonth(1));
        dpHasta.setValue(LocalDate.now());

        grupoReporte.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {

            boolean mostrar = rbVentas.isSelected();

            panelFechas.setVisible(mostrar);
            panelFechas.setManaged(mostrar);

        });

        panelFechas.setVisible(true);
        panelFechas.setManaged(true);
    }


    @FXML
    public void onGenerar() {

        lblEstado.setText("Generando reporte...");

        try {

            if (rbVentas.isSelected()) {

                tipoActual = "VENTAS";

                if (dpDesde.getValue() == null || dpHasta.getValue() == null) {
                    lblEstado.setText("Debe seleccionar ambas fechas.");
                    return;
                }

                LocalDateTime desde = dpDesde.getValue().atStartOfDay();
                LocalDateTime hasta = dpHasta.getValue().atTime(23, 59, 59);

                ventasActuales = reporteService.obtenerVentasPorPeriodo(desde, hasta);

                configurarTablaVentas();

                tablaReporte.setItems(
                        FXCollections.observableArrayList(ventasActuales));

                lblTituloReporte.setText(
                        "Ventas desde "
                                + dpDesde.getValue().format(FMT_FECHA)
                                + " hasta "
                                + dpHasta.getValue().format(FMT_FECHA));

                lblConteo.setText(ventasActuales.size() + " registros");
            }

            else if (rbInventario.isSelected()) {

                tipoActual = "INVENTARIO";

                inventarioActual = reporteService.obtenerInventario();

                configurarTablaInventario();

                tablaReporte.setItems(
                        FXCollections.observableArrayList(inventarioActual));

                lblTituloReporte.setText("Inventario actual");

                lblConteo.setText(inventarioActual.size() + " productos");
            }

            else if (rbCobrar.isSelected()) {

                tipoActual = "COBRAR";

                cobrarActuales = reporteService.obtenerCuentasPorCobrar();

                configurarTablaCobrar();

                tablaReporte.setItems(
                        FXCollections.observableArrayList(cobrarActuales));

                lblTituloReporte.setText("Cuentas por cobrar");

                lblConteo.setText(cobrarActuales.size() + " cuentas");
            }

            else if (rbPagar.isSelected()) {

                tipoActual = "PAGAR";

                pagarActuales = reporteService.obtenerCuentasPorPagar();

                configurarTablaPagar();

                tablaReporte.setItems(
                        FXCollections.observableArrayList(pagarActuales));

                lblTituloReporte.setText("Cuentas por pagar");

                lblConteo.setText(pagarActuales.size() + " cuentas");
            }

            lblEstado.setText("Reporte generado correctamente.");

        } catch (Exception ex) {

            log.error("Error generando reporte", ex);

            lblEstado.setText("Error: " + ex.getMessage());
        }
    }

    @FXML
    public void onExportarPDF() {

        if (tipoActual == null) {
            lblEstado.setText("Primero genera un reporte.");
            return;
        }

        try {

            lblEstado.setText("Generando PDF...");

            String ruta;

            switch (tipoActual) {

                case "VENTAS":

                    ruta = reporteService.exportarVentasPDF(
                            ventasActuales,
                            dpDesde.getValue().atStartOfDay(),
                            dpHasta.getValue().atTime(23,59));

                    break;

                case "INVENTARIO":

                    ruta = reporteService.exportarInventarioPDF(
                            inventarioActual);

                    break;

                case "COBRAR":

                    ruta = reporteService.exportarCobrarPDF(
                            cobrarActuales);

                    break;

                case "PAGAR":

                    ruta = reporteService.exportarPagarPDF(
                            pagarActuales);

                    break;

                default:
                    throw new IllegalStateException("Tipo de reporte desconocido.");
            }

            facturaService.abrirPDF(ruta);

            lblEstado.setText("PDF generado correctamente.");

        }

        catch (Exception ex) {

            log.error("Error exportando PDF", ex);

            lblEstado.setText("Error al exportar PDF.");
        }
    }


    // ---- Configuración de columnas por tipo ----

    @SuppressWarnings("unchecked")
    private void configurarTablaVentas() {
        tablaReporte.getColumns().clear();
        tablaReporte.getColumns().addAll(
                col("Factura",   v -> nvl(((Venta)v).getNumeroFactura())),
                col("Fecha",     v -> ((Venta)v).getFechaHora().format(FMT)),
                col("Cliente",   v -> ((Venta)v).getCliente() != null
                        ? ((Venta)v).getCliente().getNombreCompleto() : "Ocasional"),
                col("Productos", v -> ((Venta)v).getDetalles().stream()
                        .map(vp -> vp.getProducto().getNombre()
                                + " x" + vp.getCantidad())
                        .collect(Collectors.joining(", "))),
                col("Descuento", v -> {
                    BigDecimal d = ((Venta)v).getDescuentoPorcentual();
                    return d.compareTo(BigDecimal.ZERO) > 0
                            ? d.toPlainString() + "%" : "—";
                }),
                col("Total",     v -> "RD$" + ((Venta)v).getTotal().toPlainString()),
                col("Pago",      v -> ((Venta)v).getMetodoPago()),
                col("NCF",       v -> nvl(((Venta)v).getNcf()))
        );
        ajustarAnchos(new double[]{100,130,150,260,80,110,90,100});
    }

    @SuppressWarnings("unchecked")
    private void configurarTablaInventario() {
        tablaReporte.getColumns().clear();
        tablaReporte.getColumns().addAll(
                col("Producto",       p -> ((Producto)p).getNombre()),
                col("Marca",          p -> nvl(((Producto)p).getMarca())),
                col("Categoría",      p -> ((Producto)p).getCategoria() != null
                        ? ((Producto)p).getCategoria().getNombre() : "—"),
                col("Stock",          p -> String.valueOf(((Producto)p).getStock())),
                col("Mínimo",         p -> String.valueOf(((Producto)p).getStockMinimo())),
                col("Precio venta",   p -> "RD$"
                        + ((Producto)p).getPrecioUnitario().toPlainString()),
                col("Último costo",   p -> ((Producto)p).getUltimoPrecioCompra() != null
                        ? "RD$" + ((Producto)p).getUltimoPrecioCompra().toPlainString() : "—"),
                col("Precio sugerido",p -> ((Producto)p).getPrecioSugerido() != null
                        ? "RD$" + ((Producto)p).getPrecioSugerido().toPlainString() : "—")
        );
        ajustarAnchos(new double[]{180,100,110,70,70,110,110,120});
    }

    @SuppressWarnings("unchecked")
    private void configurarTablaCobrar() {
        tablaReporte.getColumns().clear();
        tablaReporte.getColumns().addAll(
                col("Factura",    v -> nvl(((Venta)v).getNumeroFactura())),
                col("Cliente",    v -> ((Venta)v).getCliente() != null
                        ? ((Venta)v).getCliente().getNombreCompleto() : "—"),
                col("Total",      v -> "RD$" + ((Venta)v).getTotal().toPlainString()),
                col("Pagado",     v -> "RD$"
                        + ((Venta)v).getTotalPagado().toPlainString()),
                col("Saldo",      v -> "RD$"
                        + ((Venta)v).getSaldoPendiente().toPlainString()),
                col("Vencimiento",v -> ((Venta)v).getFechaLimiteCredito() != null
                        ? ((Venta)v).getFechaLimiteCredito().format(FMT_FECHA) : "—"),
                col("Estado",     v -> ((Venta)v).estaCancelado() ? "Pagado"
                        : ((Venta)v).getFechaLimiteCredito() != null
                        && ((Venta)v).getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now())
                        ? "Vencido" : "Pendiente")
        );
        ajustarAnchos(new double[]{100,180,110,110,110,110,90});
    }

    @SuppressWarnings("unchecked")
    private void configurarTablaPagar() {
        tablaReporte.getColumns().clear();
        tablaReporte.getColumns().addAll(
                col("#",          c -> String.valueOf(
                        ((CuentaPorPagar)c).getIdCuenta())),
                col("Proveedor",  c -> ((CuentaPorPagar)c).getProveedor().getNombre()),
                col("Compra #",   c -> "#"
                        + ((CuentaPorPagar)c).getCompra().getIdCompra()),
                col("Total",      c -> "RD$"
                        + ((CuentaPorPagar)c).getMontoTotal().toPlainString()),
                col("Pagado",     c -> "RD$"
                        + ((CuentaPorPagar)c).getTotalPagado().toPlainString()),
                col("Saldo",      c -> "RD$"
                        + ((CuentaPorPagar)c).getSaldoPendiente().toPlainString()),
                col("Vencimiento",c -> ((CuentaPorPagar)c).getFechaLimite() != null
                        ? ((CuentaPorPagar)c).getFechaLimite().format(FMT_FECHA) : "—"),
                col("Estado",     c -> ((CuentaPorPagar)c).estaCancelada()
                        ? "Pagada" : "Pendiente")
        );
        ajustarAnchos(new double[]{50,180,80,110,110,110,110,90});
    }

    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    private TableColumn<Object, String> col(String titulo,
                                            java.util.function.Function<Object, String> extractor) {
        TableColumn<Object, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d ->
                new SimpleStringProperty(extractor.apply(d.getValue())));
        return c;
    }

    @SuppressWarnings("unchecked")
    private void ajustarAnchos(double[] anchos) {
        ObservableList<TableColumn<Object, ?>> cols = tablaReporte.getColumns();
        for (int i = 0; i < Math.min(cols.size(), anchos.length); i++) {
            cols.get(i).setPrefWidth(anchos[i]);
        }
    }

    private String nvl(String s) { return s != null ? s : "—"; }
}