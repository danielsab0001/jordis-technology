package com.jordis.jordis.controller;

import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.ProductoService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProductoFormController {

    @FXML private Text txtTitulo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtMarca;
    @FXML private TextField txtModelo;
    @FXML private TextField txtPrecio;
    @FXML private TextField txtStockMinimo;
    @FXML private TextArea  txtDescripcion;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private Label lblPrecioSugerido;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final ProductoService productoService;

    private Producto productoEditar;
    private Runnable onGuardado;

    @FXML
    public void initialize() {
        cmbCategoria.getItems().addAll(productoService.obtenerCategorias());
        txtStockMinimo.setText("3");
    }

    public void setProducto(Producto producto) {
        this.productoEditar = producto;
        if (producto != null) {
            txtTitulo.setText("Editar Producto");
            txtNombre.setText(producto.getNombre());
            txtMarca.setText(producto.getMarca() != null ? producto.getMarca() : "");
            txtModelo.setText(producto.getModelo() != null ? producto.getModelo() : "");
            txtPrecio.setText(producto.getPrecioUnitario().toPlainString());
            txtStockMinimo.setText(String.valueOf(producto.getStockMinimo()));
            txtDescripcion.setText(
                    producto.getDescripcion() != null ? producto.getDescripcion() : "");
            cmbCategoria.setValue(producto.getCategoria());

            // Mostrar precio sugerido si existe
            if (producto.getPrecioSugerido() != null) {
                lblPrecioSugerido.setText("RD$" +
                        producto.getPrecioSugerido().toPlainString()
                        + " (basado en último costo + 30%)");
            } else if (producto.getUltimoPrecioCompra() != null) {
                lblPrecioSugerido.setText("—  (aún no hay compras registradas)");
            } else {
                lblPrecioSugerido.setText("—");
            }
        } else {
            txtTitulo.setText("Nuevo Producto");
            lblPrecioSugerido.setText("—  (disponible tras la primera compra)");
        }
    }

    public void setOnGuardado(Runnable callback) { this.onGuardado = callback; }

    @FXML
    public void onGuardar() {
        String nombre = txtNombre.getText().trim();
        String marca  = txtMarca.getText().trim();
        String modelo = txtModelo.getText().trim();
        String desc   = txtDescripcion.getText().trim();
        Categoria cat = cmbCategoria.getValue();

        if (nombre.isEmpty()) {
            lblError.setText("El nombre del producto es obligatorio.");
            return;
        }

        BigDecimal precio;
        int stockMin;
        try {
            precio   = new BigDecimal(txtPrecio.getText().trim());
            stockMin = Integer.parseInt(txtStockMinimo.getText().trim());
        } catch (NumberFormatException e) {
            lblError.setText("Precio y stock mínimo deben ser números válidos.");
            return;
        }

        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            lblError.setText("El precio debe ser mayor a 0.");
            return;
        }

        try {
            if (productoEditar == null) {
                productoService.crear(nombre, desc, precio, 0, stockMin, cat, marca, modelo);
            } else {
                productoService.actualizar(
                        productoEditar.getIdProducto(),
                        nombre, desc, precio,
                        productoEditar.getStock(), // mantener stock existente
                        stockMin, cat, marca, modelo);
            }
            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}