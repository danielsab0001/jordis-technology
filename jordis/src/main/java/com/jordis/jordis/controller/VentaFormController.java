package com.jordis.jordis.controller;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.*;
import com.jordis.jordis.model.Venta;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VentaFormController {

    @FXML private ComboBox<Cliente>    cmbCliente;
    @FXML private ComboBox<String>     cmbMetodoPago;
    @FXML private ComboBox<Producto>   cmbProducto;
    @FXML private TextField            txtCantidad;
    @FXML private TextField            txtGarantiaDesc;
    @FXML private TextField            txtGarantiaMeses;
    @FXML private CheckBox             chkCredito;
    @FXML private Label                lblFechaLimite;
    @FXML private DatePicker           dpFechaLimite;
    @FXML private TableView<FilaVenta> tablaDetalle;
    @FXML private TableColumn<FilaVenta, String> colProducto;
    @FXML private TableColumn<FilaVenta, String> colCantidad;
    @FXML private TableColumn<FilaVenta, String> colPrecio;
    @FXML private TableColumn<FilaVenta, String> colGarantia;
    @FXML private TableColumn<FilaVenta, String> colSubtotal;
    @FXML private TableColumn<FilaVenta, Void>   colQuitar;
    @FXML private ComboBox<String>     cmbDescuento;
    @FXML private TextField            txtDescuentoManual;
    @FXML private Label                lblSubtotal;
    @FXML private Label                lblDescuentoLabel;
    @FXML private Label                lblDescuentoMonto;
    @FXML private Label                lblTotal;
    @FXML private TextField            txtNotas;
    @FXML private Label                lblError;
    @FXML private Button               btnGuardar;
    @FXML private CheckBox       chkCreditoFiscal;
    @FXML private Label          lblTipoNcf;
    @FXML private ComboBox<String> cmbTipoNcf;
    @FXML private Label          lblItbis;
    @FXML private ComboBox<String> cmbItbis;
    @FXML private Label lblItbisLabel;
    @FXML private Label lblItbisMonto;

    private final NCFService ncfService;

    private final VentaService         ventaService;
    private final ClienteService       clienteService;
    private final ProductoService      productoService;
    private final AutenticacionService autenticacionService;
    private final FacturaService       facturaService;

    private final ObservableList<FilaVenta> detalles =
            FXCollections.observableArrayList();
    private List<Producto> todosLosProductos;
    private Runnable onGuardado;

    @FXML
    public void initialize() {
        configurarClientes();
        configurarMetodoPago();
        configurarProductoEditable();
        configurarDescuento();
        configurarTabla();
        tablaDetalle.setItems(detalles);
        cmbTipoNcf.getItems().setAll(ncfService.obtenerTiposDisponibles());
        cmbTipoNcf.setValue("B01 — Crédito Fiscal");
        cmbItbis.getItems().setAll("0%", "18%"); // 18% es el ITBIS estándar en RD
        cmbItbis.setValue("18%");
        cmbItbis.setOnAction(e -> actualizarTotales());
    }

    public void prepararNuevaVenta() {
        detalles.clear();

        // Recargar clientes
        cmbCliente.getItems().clear();
        cmbCliente.getItems().add(null);
        cmbCliente.getItems().addAll(clienteService.obtenerTodos());
        cmbCliente.setValue(null);

        // Recargar productos
        todosLosProductos = productoService.obtenerTodos();
        cmbProducto.getItems().setAll(todosLosProductos);
        cmbProducto.setValue(null);
        if (cmbProducto.isEditable()) cmbProducto.getEditor().clear();

        cmbMetodoPago.setValue("EFECTIVO");
        cmbDescuento.setValue("0%");
        chkCredito.setSelected(false);
        dpFechaLimite.setValue(null);
        lblFechaLimite.setVisible(false);
        lblFechaLimite.setManaged(false);
        dpFechaLimite.setVisible(false);
        dpFechaLimite.setManaged(false);
        chkCreditoFiscal.setSelected(false);
        onToggleCreditoFiscal();

        txtCantidad.clear();
        txtGarantiaDesc.clear();
        txtGarantiaMeses.clear();
        txtNotas.clear();
        txtDescuentoManual.clear();
        txtDescuentoManual.setDisable(true);
        lblDescuentoLabel.setText("Descuento (0%):");
        lblDescuentoMonto.setText("—");
        lblSubtotal.setText("RD$0.00");
        lblTotal.setText("RD$0.00");
        lblError.setText("");
    }

    private void configurarClientes() {
        cmbCliente.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Cliente c) {
                if (c == null) return "Sin cliente (ocasional)";
                return c.getNombreCompleto()
                        + (c.esEmpresa() ? " [Empresa]" : "");
            }
            @Override public Cliente fromString(String s) { return null; }
        });

        // Si selecciona empresa, ofrece crédito automáticamente
        cmbCliente.setOnAction(e -> {
            Cliente seleccionado = cmbCliente.getValue();
            if (seleccionado != null && seleccionado.esEmpresa()) {
                chkCredito.setDisable(false);
            } else {
                chkCredito.setSelected(false);
                chkCredito.setDisable(true);
                ocultarCredito();
            }
        });
    }

    private void configurarMetodoPago() {
        cmbMetodoPago.getItems().setAll(
                "EFECTIVO", "TARJETA", "TRANSFERENCIA");
        cmbMetodoPago.setValue("EFECTIVO");
    }

    private void configurarProductoEditable() {
        cmbProducto.setEditable(true);
        cmbProducto.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) {
                if (p == null) return "";
                return p.getNombre()
                        + (p.getMarca() != null ? " — " + p.getMarca() : "")
                        + " (Stock: " + p.getStock() + ")"
                        + " — RD$" + p.getPrecioUnitario().toPlainString();
            }
            @Override public Producto fromString(String texto) {
                if (texto == null || texto.isBlank() || todosLosProductos == null)
                    return null;
                return todosLosProductos.stream()
                        .filter(p -> toString(p).equalsIgnoreCase(texto))
                        .findFirst().orElse(null);
            }
        });

        cmbProducto.getEditor().textProperty().addListener((obs, old, val) -> {
            if (todosLosProductos == null) return;
            // No filtrar si el cambio viene de seleccionar un item
            Producto sel = cmbProducto.getValue();
            if (sel != null) {
                String textoSel = cmbProducto.getConverter().toString(sel);
                if (textoSel.equals(val)) return;
            }
            if (val == null || val.isBlank()) {
                cmbProducto.getItems().setAll(todosLosProductos);
                return;
            }
            String filtro = val.toLowerCase();
            List<Producto> filtrados = todosLosProductos.stream()
                    .filter(p -> p.getNombre().toLowerCase().contains(filtro)
                            || (p.getMarca() != null &&
                            p.getMarca().toLowerCase().contains(filtro)))
                    .toList();
            cmbProducto.getItems().setAll(filtrados);
            if (!cmbProducto.isShowing() && !filtrados.isEmpty()) {
                cmbProducto.show();
            }
        });
    }

    private void configurarDescuento() {
        cmbDescuento.getItems().setAll(
                "0%", "5%", "10%", "15%", "20%", "Manual");
        cmbDescuento.setValue("0%");
        cmbDescuento.setOnAction(e -> {
            boolean esManual = "Manual".equals(cmbDescuento.getValue());
            txtDescuentoManual.setDisable(!esManual);
            if (!esManual) {
                txtDescuentoManual.clear();
                txtDescuentoManual.setStyle(
                        "-fx-border-color: #BFDBFE; -fx-border-radius: 6; "
                                + "-fx-background-radius: 6; -fx-padding: 4 8;");
                lblError.setText("");
            }
            actualizarTotales();
        });

        txtDescuentoManual.setDisable(true);
        txtDescuentoManual.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                restaurarEstiloDescuento(); actualizarTotales(); return;
            }
            try {
                BigDecimal d = new BigDecimal(val);
                if (d.compareTo(BigDecimal.ZERO) < 0 ||
                        d.compareTo(new BigDecimal("100")) > 0) {
                    txtDescuentoManual.setStyle(
                            "-fx-border-color: #DC2626; -fx-border-radius: 6; "
                                    + "-fx-background-radius: 6; -fx-padding: 4 8;");
                    lblError.setText("El descuento debe estar entre 0% y 100%.");
                } else {
                    restaurarEstiloDescuento();
                    lblError.setText("");
                }
            } catch (NumberFormatException ignored) {
                txtDescuentoManual.setStyle(
                        "-fx-border-color: #DC2626; -fx-border-radius: 6; "
                                + "-fx-background-radius: 6; -fx-padding: 4 8;");
            }
            actualizarTotales();
        });
    }

    private void restaurarEstiloDescuento() {
        txtDescuentoManual.setStyle(
                "-fx-border-color: #BFDBFE; -fx-border-radius: 6; "
                        + "-fx-background-radius: 6; -fx-padding: 4 8;");
    }

    private void configurarTabla() {
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().producto.getNombre()));
        colCantidad.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidad)));
        colPrecio.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().producto.getPrecioUnitario().toPlainString()));
        colGarantia.setCellValueFactory(d -> {
            String g = d.getValue().garantiaDesc;
            int m = d.getValue().garantiaMeses;
            if (g == null || g.isBlank()) return new SimpleStringProperty("—");
            return new SimpleStringProperty(g + (m > 0 ? " (" + m + " m.)" : ""));
        });
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().subtotal().toPlainString()));

        colQuitar.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;"
                        + " -fx-border-color: #FCA5A5; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 11;"
                        + " -fx-padding: 2 6;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                FilaVenta fila = (FilaVenta) getTableRow().getItem();
                btn.setOnAction(e -> {
                    detalles.remove(fila);
                    actualizarTotales();
                });
                setGraphic(btn);
            }
        });
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onToggleCredito() {
        boolean esCredito = chkCredito.isSelected();
        if (esCredito) {
            lblFechaLimite.setVisible(true);
            lblFechaLimite.setManaged(true);
            dpFechaLimite.setVisible(true);
            dpFechaLimite.setManaged(true);
            cmbMetodoPago.setDisable(true);
            cmbMetodoPago.setValue("CREDITO");
        } else {
            ocultarCredito();
        }
    }

    private void ocultarCredito() {
        lblFechaLimite.setVisible(false);
        lblFechaLimite.setManaged(false);
        dpFechaLimite.setVisible(false);
        dpFechaLimite.setManaged(false);
        cmbMetodoPago.setDisable(false);
        cmbMetodoPago.setValue("EFECTIVO");
    }

    @FXML
    public void onToggleCreditoFiscal() {
        boolean activo = chkCreditoFiscal.isSelected();
        lblTipoNcf.setVisible(activo); lblTipoNcf.setManaged(activo);
        cmbTipoNcf.setVisible(activo); cmbTipoNcf.setManaged(activo);
        lblItbis.setVisible(activo);   lblItbis.setManaged(activo);
        cmbItbis.setVisible(activo);   cmbItbis.setManaged(activo);
        actualizarTotales(); // recalcular al cambiar
    }

    @FXML
    public void onAgregarProducto() {
        lblError.setText("");
        Producto producto = cmbProducto.getValue();
        if (producto == null) {
            lblError.setText("Selecciona un producto de la lista."); return;
        }

        try {
            int cantidad = Integer.parseInt(
                    txtCantidad.getText().isBlank() ? "1" : txtCantidad.getText().trim());

            if (cantidad <= 0) {
                lblError.setText("La cantidad debe ser mayor a 0."); return;
            }
            if (cantidad > producto.getStock()) {
                lblError.setText("Stock insuficiente. Disponible: "
                        + producto.getStock()); return;
            }

            String garantiaDesc  = txtGarantiaDesc.getText().trim();
            int    garantiaMeses = 0;
            try {
                String m = txtGarantiaMeses.getText().trim();
                if (!m.isEmpty()) garantiaMeses = Integer.parseInt(m);
            } catch (NumberFormatException ignored) {}

            detalles.removeIf(f -> f.producto.getIdProducto()
                    .equals(producto.getIdProducto()));
            detalles.add(new FilaVenta(producto, cantidad,
                    garantiaDesc, garantiaMeses));
            actualizarTotales();

            // Limpiar campos de agregar
            txtCantidad.clear();
            txtGarantiaDesc.clear();
            txtGarantiaMeses.clear();
            cmbProducto.setValue(null);
            cmbProducto.getEditor().clear();
            cmbProducto.getItems().setAll(todosLosProductos);

        } catch (NumberFormatException e) {
            lblError.setText("La cantidad debe ser un número entero.");
        }
    }

    @FXML
    public void onGuardar() {
        lblError.setText("");

        if (detalles.isEmpty()) {
            lblError.setText("Agrega al menos un producto."); return;
        }

        boolean esCredito = chkCredito.isSelected();
        Cliente clienteSel = cmbCliente.getValue();

        if (esCredito) {
            if (clienteSel == null) {
                lblError.setText(
                        "Las ventas a crédito requieren un cliente registrado."); return;
            }
            if (!clienteSel.esEmpresa()) {
                lblError.setText(
                        "Las ventas a crédito solo están disponibles para empresas."); return;
            }
            if (dpFechaLimite.getValue() == null) {
                lblError.setText("Indica la fecha límite de pago."); return;
            }
        }

        BigDecimal descuento = obtenerDescuento();
        if (descuento.compareTo(BigDecimal.ZERO) < 0
                || descuento.compareTo(new BigDecimal("100")) > 0) {
            lblError.setText("El descuento debe estar entre 0% y 100%."); return;
        }

        Map<Integer, Integer>  items     = new HashMap<>();
        Map<Integer, String[]> garantias = new HashMap<>();
        for (FilaVenta f : detalles) {
            items.put(f.producto.getIdProducto(), f.cantidad);
            if (f.garantiaDesc != null && !f.garantiaDesc.isBlank()) {
                garantias.put(f.producto.getIdProducto(),
                        new String[]{f.garantiaDesc,
                                String.valueOf(f.garantiaMeses)});
            }
        }

        LocalDateTime fechaLimite = null;
        if (esCredito && dpFechaLimite.getValue() != null) {
            fechaLimite = dpFechaLimite.getValue()
                    .atTime(23, 59, 59);
        }

        String metodoPago = esCredito
                ? "CREDITO" : cmbMetodoPago.getValue();

        boolean esCreditoFiscal = chkCreditoFiscal.isSelected();
        String tipoNcf = null;
        BigDecimal itbis = BigDecimal.ZERO;

        if (esCreditoFiscal) {
            tipoNcf = ncfService.extraerCodigo(cmbTipoNcf.getValue());
            try {
                itbis = new BigDecimal(
                        cmbItbis.getValue().replace("%", "").trim());
            } catch (Exception e) {
                itbis = BigDecimal.ZERO;
            }
        }

        try {
            Venta venta = ventaService.registrarVenta(
                    clienteSel != null ? clienteSel.getIdCliente() : null,
                    autenticacionService.getUsuarioActivo(),
                    metodoPago,
                    descuento,
                    items,
                    garantias,
                    txtNotas.getText().trim(),
                    esCredito,
                    fechaLimite,
                    esCreditoFiscal,
                    tipoNcf,
                    itbis
            );

            // Preguntar si desea imprimir la factura
            Alert pregunta = new Alert(Alert.AlertType.CONFIRMATION,
                    "Venta registrada correctamente.\n¿Deseas generar la factura PDF?",
                    ButtonType.YES, ButtonType.NO);
            pregunta.setTitle("Factura");
            pregunta.setHeaderText("Factura " + venta.getNumeroFactura());
            pregunta.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    try {
                        String ruta = facturaService.generarFactura(venta);
                        facturaService.abrirPDF(ruta);
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR,
                                "Error generando PDF: " + ex.getMessage())
                                .showAndWait();
                    }
                }
            });

            if (onGuardado != null) onGuardado.run();
            cerrar();

        } catch (VentaService.StockInsuficienteException
                 | VentaService.VentaInvalidaException e) {
            lblError.setText(e.getMessage());
        } catch (Exception e) {
            lblError.setText("Error inesperado: " + e.getMessage());
            log.error("Error registrando venta", e);
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private BigDecimal obtenerDescuento() {
        String sel = cmbDescuento.getValue();
        if ("Manual".equals(sel)) {
            try {
                BigDecimal d = new BigDecimal(txtDescuentoManual.getText().trim());
                if (d.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                if (d.compareTo(new BigDecimal("100")) > 0) return new BigDecimal("100");
                return d;
            } catch (Exception e) { return BigDecimal.ZERO; }
        }
        try {
            return new BigDecimal(sel.replace("%", "").trim());
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private void actualizarTotales() {
        BigDecimal subtotal = detalles.stream()
                .map(FilaVenta::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal descPct = obtenerDescuento();
        BigDecimal montoDesc = subtotal.multiply(descPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalSinItbis = subtotal.subtract(montoDesc)
                .setScale(2, RoundingMode.HALF_UP);

        // Calcular ITBIS si está activo
        BigDecimal itbisPct = BigDecimal.ZERO;
        BigDecimal montoItbis = BigDecimal.ZERO;
        if (chkCreditoFiscal.isSelected() && cmbItbis.getValue() != null) {
            try {
                itbisPct = new BigDecimal(
                        cmbItbis.getValue().replace("%", "").trim());
                montoItbis = totalSinItbis.multiply(itbisPct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }

        BigDecimal totalFinal = totalSinItbis.add(montoItbis);

        lblSubtotal.setText("RD$" + subtotal.setScale(2).toPlainString());

        if (descPct.compareTo(BigDecimal.ZERO) > 0) {
            lblDescuentoLabel.setText("Descuento ("
                    + descPct.stripTrailingZeros().toPlainString() + "%):");
            lblDescuentoMonto.setText("- RD$" + montoDesc.toPlainString());
        } else {
            lblDescuentoLabel.setText("Descuento (0%):");
            lblDescuentoMonto.setText("—");
        }

        // Mostrar/ocultar ITBIS
        boolean hayItbis = montoItbis.compareTo(BigDecimal.ZERO) > 0;
        lblItbisLabel.setVisible(hayItbis);
        lblItbisLabel.setManaged(hayItbis);
        lblItbisMonto.setVisible(hayItbis);
        lblItbisMonto.setManaged(hayItbis);
        if (hayItbis) {
            lblItbisLabel.setText("ITBIS ("
                    + itbisPct.toPlainString() + "%):");
            lblItbisMonto.setText("+ RD$" + montoItbis.toPlainString());
        }

        lblTotal.setText("RD$" + totalFinal.toPlainString());
    }
    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }

    record FilaVenta(
            Producto producto,
            int cantidad,
            String garantiaDesc,
            int garantiaMeses
    ) {
        BigDecimal subtotal() {
            return producto.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(cantidad));
        }
    }
}