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
import java.util.HashMap;
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

    @FXML
    public void initialize() {
        configurarTabla();
        tablaDetalle.setItems(detalles);

        cmbProducto.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) {
                if (p == null) return "";
                return p.getNombre()
                        + (p.getMarca() != null ? " — " + p.getMarca() : "");
            }
            @Override public Producto fromString(String s) { return null; }
        });

        cmbProveedor.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Proveedor p) {
                return p == null ? "" : p.getNombre();
            }
            @Override public Proveedor fromString(String s) { return null; }
        });
    }

    // Llamado desde CompraController antes de mostrar la ventana
    public void prepararNuevaCompra() {
        detalles.clear();

        cmbProveedor.getItems().setAll(proveedorService.obtenerTodos());
        cmbProveedor.setValue(null);

        cmbProducto.getItems().setAll(productoService.obtenerTodos());
        cmbProducto.setValue(null);

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
        Producto producto = cmbProducto.getValue();
        if (producto == null) { lblError.setText("Selecciona un producto."); return; }

        try {
            int cantidad     = Integer.parseInt(txtCantidad.getText().trim());
            BigDecimal costo = new BigDecimal(txtCosto.getText().trim());

            if (cantidad <= 0 || costo.compareTo(BigDecimal.ZERO) <= 0) {
                lblError.setText("Cantidad y costo deben ser mayores a 0.");
                return;
            }

            detalles.removeIf(f -> f.producto.getIdProducto()
                    .equals(producto.getIdProducto()));
            detalles.add(new FilaDetalle(producto, cantidad, costo));
            actualizarTotal();
            txtCantidad.clear();
            txtCosto.clear();
            cmbProducto.setValue(null);

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
                    items);
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