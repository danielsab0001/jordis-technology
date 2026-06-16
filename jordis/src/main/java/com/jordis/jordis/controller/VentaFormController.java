package com.jordis.jordis.controller;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.ClienteService;
import com.jordis.jordis.service.ProductoService;
import com.jordis.jordis.service.VentaService;
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
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VentaFormController {

    @FXML private ComboBox<Cliente>    cmbCliente;
    @FXML private ComboBox<String>     cmbMetodoPago;
    @FXML private ComboBox<Producto>   cmbProducto;
    @FXML private TextField            txtCantidad;
    @FXML private TableView<FilaVenta> tablaDetalle;
    @FXML private TableColumn<FilaVenta, String> colProducto;
    @FXML private TableColumn<FilaVenta, String> colCantidad;
    @FXML private TableColumn<FilaVenta, String> colPrecio;
    @FXML private TableColumn<FilaVenta, String> colSubtotal;
    @FXML private TableColumn<FilaVenta, Void>   colQuitar;
    @FXML private ComboBox<String>     cmbDescuento;
    @FXML private TextField            txtDescuentoManual;
    @FXML private Label                lblSubtotal;
    @FXML private Label                lblTotal;
    @FXML private Label                lblError;
    @FXML private Button               btnGuardar;
    @FXML private Label lblDescuentoLabel;
    @FXML private Label lblDescuentoMonto;

    private final VentaService         ventaService;
    private final ClienteService       clienteService;
    private final ProductoService      productoService;
    private final AutenticacionService autenticacionService;

    private final ObservableList<FilaVenta> detalles =
            FXCollections.observableArrayList();
    private Runnable onGuardado;

    @FXML
    public void initialize() {
        configurarConverters();
        configurarDescuento();
        configurarTabla();
        tablaDetalle.setItems(detalles);
    }

    // Llamado desde VentaController antes de mostrar la ventana
    public void prepararNuevaVenta() {
        detalles.clear();

        // Recargar datos frescos
        cmbCliente.getItems().clear();
        cmbCliente.getItems().add(null);
        cmbCliente.getItems().addAll(clienteService.obtenerTodos());
        cmbCliente.setValue(null);

        cmbProducto.getItems().setAll(productoService.obtenerTodos());
        cmbProducto.setValue(null);

        cmbMetodoPago.setValue("EFECTIVO");
        cmbDescuento.setValue("0%");

        txtCantidad.clear();
        txtDescuentoManual.clear();
        txtDescuentoManual.setDisable(true);
        txtDescuentoManual.setStyle(
                "-fx-border-color: #BFDBFE; -fx-border-radius: 6; "
                        + "-fx-background-radius: 6; -fx-padding: 4 8;");

        lblSubtotal.setText("RD$0.00");
        lblTotal.setText("RD$0.00");
        lblError.setText("");

        // Al final del método prepararNuevaVenta() agrega:
        lblDescuentoLabel.setText("Descuento (0%):");
        lblDescuentoMonto.setText("—");
    }

    private void configurarConverters() {
        cmbCliente.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Cliente c) {
                return c == null ? "Sin cliente (ocasional)" : c.getNombreCompleto();
            }
            @Override public Cliente fromString(String s) { return null; }
        });

        cmbMetodoPago.getItems().setAll("EFECTIVO", "TARJETA", "TRANSFERENCIA");
        cmbMetodoPago.setValue("EFECTIVO");

        cmbProducto.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) {
                return p == null ? "" : p.getNombre()
                        + " (Stock: " + p.getStock() + ") — RD$"
                        + p.getPrecioUnitario().toPlainString();
            }
            @Override public Producto fromString(String s) { return null; }
        });
    }

    private void configurarDescuento() {
        cmbDescuento.getItems().setAll("0%", "5%", "10%", "15%", "20%", "Manual");
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
                actualizarTotales();
            }
        });

        txtDescuentoManual.setDisable(true);
        txtDescuentoManual.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                txtDescuentoManual.setStyle(
                        "-fx-border-color: #BFDBFE; -fx-border-radius: 6; "
                                + "-fx-background-radius: 6; -fx-padding: 4 8;");
                lblError.setText("");
                actualizarTotales();
                return;
            }
            try {
                BigDecimal d = new BigDecimal(val);
                if (d.compareTo(BigDecimal.ZERO) < 0) {
                    marcarDescuentoError("El descuento no puede ser negativo.");
                } else if (d.compareTo(new BigDecimal("100")) > 0) {
                    marcarDescuentoError("El descuento no puede superar el 100%.");
                } else {
                    txtDescuentoManual.setStyle(
                            "-fx-border-color: #BFDBFE; -fx-border-radius: 6; "
                                    + "-fx-background-radius: 6; -fx-padding: 4 8;");
                    lblError.setText("");
                }
            } catch (NumberFormatException ignored) {
                marcarDescuentoError("El descuento debe ser un número.");
            }
            actualizarTotales();
        });
    }

    private void marcarDescuentoError(String mensaje) {
        txtDescuentoManual.setStyle(
                "-fx-border-color: #DC2626; -fx-border-radius: 6; "
                        + "-fx-background-radius: 6; -fx-padding: 4 8;");
        lblError.setText(mensaje);
    }

    private void configurarTabla() {
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().producto.getNombre()));
        colCantidad.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidad)));
        colPrecio.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().producto.getPrecioUnitario().toPlainString()));
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().subtotal().toPlainString()));

        colQuitar.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;"
                        + " -fx-border-color: #FCA5A5; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 11; -fx-padding: 2 6;");
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
    public void onAgregarProducto() {
        lblError.setText("");
        Producto producto = cmbProducto.getValue();
        if (producto == null) { lblError.setText("Selecciona un producto."); return; }

        try {
            int cantidad = Integer.parseInt(
                    txtCantidad.getText().isBlank() ? "1" : txtCantidad.getText().trim());

            if (cantidad <= 0) {
                lblError.setText("La cantidad debe ser mayor a 0."); return;
            }
            if (cantidad > producto.getStock()) {
                lblError.setText("Stock insuficiente. Disponible: " + producto.getStock());
                return;
            }

            detalles.removeIf(f -> f.producto.getIdProducto()
                    .equals(producto.getIdProducto()));
            detalles.add(new FilaVenta(producto, cantidad));
            actualizarTotales();
            txtCantidad.clear();
            cmbProducto.setValue(null);

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
        if (cmbMetodoPago.getValue() == null) {
            lblError.setText("Selecciona un método de pago."); return;
        }

        // Validar descuento
        BigDecimal descuento = obtenerDescuento();
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            lblError.setText("El descuento no puede ser negativo."); return;
        }
        if (descuento.compareTo(new BigDecimal("100")) > 0) {
            lblError.setText("El descuento no puede superar el 100%."); return;
        }

        Map<Integer, Integer> items = new HashMap<>();
        for (FilaVenta f : detalles) {
            items.put(f.producto.getIdProducto(), f.cantidad);
        }

        Cliente clienteSeleccionado = cmbCliente.getValue();

        try {
            ventaService.registrarVenta(
                    clienteSeleccionado != null ? clienteSeleccionado.getIdCliente() : null,
                    autenticacionService.getUsuarioActivo(),
                    cmbMetodoPago.getValue(),
                    descuento,
                    items);
            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (VentaService.StockInsuficienteException e) {
            lblError.setText(e.getMessage());
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private BigDecimal obtenerDescuento() {
        String sel = cmbDescuento.getValue();
        if ("Manual".equals(sel)) {
            try {
                BigDecimal d = new BigDecimal(txtDescuentoManual.getText().trim());
                // Clamp 0–100
                if (d.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                if (d.compareTo(new BigDecimal("100")) > 0) return new BigDecimal("100");
                return d;
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        try {
            return new BigDecimal(sel.replace("%", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void actualizarTotales() {
        BigDecimal subtotal = detalles.stream()
                .map(FilaVenta::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal descuentoPct = obtenerDescuento();
        BigDecimal montoDescuento = subtotal
                .multiply(descuentoPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(montoDescuento)
                .setScale(2, RoundingMode.HALF_UP);

        lblSubtotal.setText("RD$" + subtotal.setScale(2).toPlainString());

        if (descuentoPct.compareTo(BigDecimal.ZERO) > 0) {
            lblDescuentoLabel.setText("Descuento (" +
                    descuentoPct.stripTrailingZeros().toPlainString() + "%):");
            lblDescuentoMonto.setText("- RD$" + montoDescuento.toPlainString());
            lblDescuentoLabel.setVisible(true);
            lblDescuentoMonto.setVisible(true);
        } else {
            lblDescuentoLabel.setText("Descuento (0%):");
            lblDescuentoMonto.setText("—");
        }

        lblTotal.setText("RD$" + total.toPlainString());
    }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }

    record FilaVenta(Producto producto, int cantidad) {
        BigDecimal subtotal() {
            return producto.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(cantidad));
        }
    }
}