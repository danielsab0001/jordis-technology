package com.jordis.jordis.controller;

import com.jordis.jordis.model.Producto;
import com.jordis.jordis.model.Proveedor;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.CompraService;
import com.jordis.jordis.service.ProductoService;
import com.jordis.jordis.service.ProveedorService;
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
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompraFormController {

    @FXML private ComboBox<Proveedor>    cmbProveedor;
    @FXML private ComboBox<Producto>     cmbProducto;
    @FXML private TextField              txtCantidad;
    @FXML private TextField              txtCosto;
    @FXML private TextArea               txtDescripcion;
    @FXML private TableView<FilaDetalle> tablaDetalle;
    @FXML private TableColumn<FilaDetalle, String> colProducto;
    @FXML private TableColumn<FilaDetalle, String> colCantidad;
    @FXML private TableColumn<FilaDetalle, String> colCosto;
    @FXML private TableColumn<FilaDetalle, String> colSubtotal;
    @FXML private TableColumn<FilaDetalle, Void>   colQuitar;
    @FXML private Label lblTotal;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final CompraService        compraService;
    private final ProveedorService     proveedorService;
    private final ProductoService      productoService;
    private final AutenticacionService autenticacionService;

    private final ObservableList<FilaDetalle> detalles =
            FXCollections.observableArrayList();
    private Runnable onGuardado;
    private List<Producto> todosLosProductos;

    @FXML
    public void initialize() {
        configurarTabla();
        tablaDetalle.setItems(detalles);

        cmbProveedor.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Proveedor p) {
                return p == null ? "" : p.getNombre();
            }
            @Override public Proveedor fromString(String s) { return null; }
        });

        // ComboBox editable con búsqueda en tiempo real
        cmbProducto.setEditable(true);
        cmbProducto.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) {
                if (p == null) return "";
                return p.getNombre()
                        + (p.getMarca() != null ? " — " + p.getMarca() : "");
            }
            @Override public Producto fromString(String texto) {
                if (texto == null || texto.isBlank()) return null;
                // Intentar encontrar el producto que coincida con el texto escrito
                return todosLosProductos == null ? null :
                        todosLosProductos.stream()
                                .filter(p -> toString(p).equalsIgnoreCase(texto))
                                .findFirst().orElse(null);
            }
        });

        cmbProducto.getEditor().textProperty().addListener((obs, old, val) -> {
            if (todosLosProductos == null) return;
            if (val == null || val.isBlank()) {
                cmbProducto.getItems().setAll(todosLosProductos);
                return;
            }
            // Solo filtrar si el texto no viene de seleccionar un item
            Producto seleccionado = cmbProducto.getValue();
            if (seleccionado != null) {
                String textoSeleccionado = cmbProducto.getConverter().toString(seleccionado);
                if (textoSeleccionado.equals(val)) return;
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

    public void prepararNuevaCompra() {
        detalles.clear();

        cmbProveedor.getItems().setAll(proveedorService.obtenerTodos());
        cmbProveedor.setValue(null);

        todosLosProductos = productoService.obtenerTodos();
        cmbProducto.getItems().setAll(todosLosProductos);
        cmbProducto.setValue(null);
        cmbProducto.getEditor().clear();

        txtCantidad.clear();
        txtCosto.clear();
        if (txtDescripcion != null) txtDescripcion.clear();
        lblTotal.setText("RD$0.00");
        lblError.setText("");
    }

    private void configurarTabla() {
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().producto.getNombre()));
        colCantidad.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidad)));
        colCosto.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().costo.toPlainString()));
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().subtotal().toPlainString()));

        colQuitar.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Quitar");
            {
                btn.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;"
                        + " -fx-border-color: #FCA5A5; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 2 6;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                FilaDetalle fila = (FilaDetalle) getTableRow().getItem();
                btn.setOnAction(e -> {
                    detalles.remove(fila);
                    actualizarTotal();
                });
                setGraphic(btn);
            }
        });
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onAgregarProducto() {
        lblError.setText("");

        // Obtener el producto seleccionado — puede estar en getValue() o en el editor
        Producto producto = cmbProducto.getValue();
        if (producto == null) {
            lblError.setText("Selecciona un producto de la lista."); return;
        }

        try {
            String cantStr = txtCantidad.getText().trim();
            String costoStr = txtCosto.getText().trim();

            if (cantStr.isEmpty() || costoStr.isEmpty()) {
                lblError.setText("Completa la cantidad y el costo."); return;
            }

            int cantidad     = Integer.parseInt(cantStr);
            BigDecimal costo = new BigDecimal(costoStr);

            if (cantidad <= 0 || costo.compareTo(BigDecimal.ZERO) <= 0) {
                lblError.setText("Cantidad y costo deben ser mayores a 0.");
                return;
            }

            // Validar precio inusual
            if (producto.getUltimoPrecioCompra() != null
                    && producto.getUltimoPrecioCompra().compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal anterior = producto.getUltimoPrecioCompra();
                BigDecimal diferencia = costo.subtract(anterior).abs()
                        .divide(anterior, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                if (diferencia.compareTo(new BigDecimal("25")) >= 0) {
                    Alert aviso = new Alert(Alert.AlertType.CONFIRMATION);
                    aviso.setTitle("Precio inusual detectado");
                    aviso.setHeaderText("⚠ El precio difiere un "
                            + diferencia.setScale(1, RoundingMode.HALF_UP) + "% del último.");
                    aviso.setContentText(
                            "Último precio: RD$" + anterior.toPlainString()
                                    + "\nPrecio ingresado: RD$" + costo.toPlainString()
                                    + "\n\n¿Deseas continuar con este precio?");

                    ButtonType btnSi = new ButtonType("Sí, usar este precio",
                            ButtonBar.ButtonData.YES);
                    ButtonType btnNo = new ButtonType("No, corregir",
                            ButtonBar.ButtonData.NO);
                    aviso.getButtonTypes().setAll(btnSi, btnNo);

                    var resultado = aviso.showAndWait();
                    if (resultado.isPresent()
                            && resultado.get().getButtonData() == ButtonBar.ButtonData.NO) {
                        txtCosto.requestFocus();
                        txtCosto.selectAll();
                        return;
                    }
                }
            }

            detalles.removeIf(f -> f.producto.getIdProducto()
                    .equals(producto.getIdProducto()));
            detalles.add(new FilaDetalle(producto, cantidad, costo));
            actualizarTotal();

            // Limpiar campos
            txtCantidad.clear();
            txtCosto.clear();
            cmbProducto.setValue(null);
            cmbProducto.getEditor().clear();
            cmbProducto.getItems().setAll(todosLosProductos);

        } catch (NumberFormatException e) {
            lblError.setText("Cantidad y costo deben ser números válidos.");
        }
    }

    @FXML
    public void onGuardar() {
        lblError.setText("");
        if (cmbProveedor.getValue() == null) {
            lblError.setText("Selecciona un proveedor."); return;
        }
        if (detalles.isEmpty()) {
            lblError.setText("Agrega al menos un producto."); return;
        }

        Map<Integer, BigDecimal[]> items = new HashMap<>();
        for (FilaDetalle f : detalles) {
            items.put(f.producto.getIdProducto(),
                    new BigDecimal[]{BigDecimal.valueOf(f.cantidad), f.costo});
        }

        String descripcion = txtDescripcion != null
                ? txtDescripcion.getText().trim() : "";

        try {
            compraService.registrarCompra(
                    cmbProveedor.getValue().getIdProveedor(),
                    autenticacionService.getUsuarioActivo().getIdUsuario(),
                    items,
                    descripcion);
            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void actualizarTotal() {
        BigDecimal total = detalles.stream()
                .map(FilaDetalle::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotal.setText("RD$" + total.setScale(2).toPlainString());
    }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }

    record FilaDetalle(Producto producto, int cantidad, BigDecimal costo) {
        BigDecimal subtotal() {
            return costo.multiply(BigDecimal.valueOf(cantidad));
        }
    }
}