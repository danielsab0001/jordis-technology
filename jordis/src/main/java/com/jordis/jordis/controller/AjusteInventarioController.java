package com.jordis.jordis.controller;

import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.InventarioService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AjusteInventarioController {

    @FXML private Label    lblProducto;
    @FXML private Label    lblStockActual;
    @FXML private Label    lblStockResultante;
    @FXML private RadioButton rbEntrada;
    @FXML private RadioButton rbSalida;
    @FXML private TextField   txtCantidad;
    @FXML private ComboBox<String> cmbMotivo;
    @FXML private VBox     panelOtraRazon;
    @FXML private TextArea txtOtraRazon;
    @FXML private Label    lblError;
    @FXML private Button   btnGuardar;

    private final InventarioService    inventarioService;
    private final AutenticacionService autenticacionService;

    private Producto producto;
    private Runnable onGuardado;

    // Motivos de entrada
    private static final String[] MOTIVOS_ENTRADA = {
            "Compra recibida",
            "Devolución de cliente",
            "Corrección de inventario — faltaba registrar",
            "Transferencia desde otra sucursal",
            "Producto encontrado en almacén",
            "Otra razón"
    };

    // Motivos de salida
    private static final String[] MOTIVOS_SALIDA = {
            "Producto dañado / deteriorado",
            "Producto vencido",
            "Robo o pérdida",
            "Muestra o uso interno",
            "Corrección de inventario — estaba de más",
            "Devolución a proveedor",
            "Otra razón"
    };

    @FXML
    public void initialize() {
        // Cambiar motivos según tipo de operación
        rbEntrada.selectedProperty().addListener((obs, old, val) -> {
            if (val) actualizarMotivos(true);
        });
        rbSalida.selectedProperty().addListener((obs, old, val) -> {
            if (val) actualizarMotivos(false);
        });

        // Mostrar panel de otra razón solo si se selecciona
        cmbMotivo.setOnAction(e -> {
            boolean esOtra = "Otra razón".equals(cmbMotivo.getValue());
            panelOtraRazon.setVisible(esOtra);
            panelOtraRazon.setManaged(esOtra);
        });

        // Calcular stock resultante al escribir cantidad
        txtCantidad.textProperty().addListener((obs, old, val) ->
                actualizarStockResultante());
        rbEntrada.selectedProperty().addListener((obs, old, val) ->
                actualizarStockResultante());
        rbSalida.selectedProperty().addListener((obs, old, val) ->
                actualizarStockResultante());

        actualizarMotivos(true);
    }

    private void actualizarMotivos(boolean esEntrada) {
        String valorActual = cmbMotivo.getValue();
        cmbMotivo.getItems().setAll(
                esEntrada ? MOTIVOS_ENTRADA : MOTIVOS_SALIDA);
        // Mantener selección si sigue siendo válida
        if (valorActual != null && cmbMotivo.getItems().contains(valorActual)) {
            cmbMotivo.setValue(valorActual);
        } else {
            cmbMotivo.setValue(null);
            panelOtraRazon.setVisible(false);
            panelOtraRazon.setManaged(false);
        }
    }

    private void actualizarStockResultante() {
        if (producto == null) return;
        try {
            int cantidad = Integer.parseInt(txtCantidad.getText().trim());
            int actual   = producto.getStock();
            int resultado = rbEntrada.isSelected()
                    ? actual + cantidad : actual - cantidad;

            lblStockResultante.setText(String.valueOf(resultado));
            lblStockResultante.setStyle(resultado < 0
                    ? "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #DC2626;"
                    : resultado <= producto.getStockMinimo()
                    ? "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #D97706;"
                    : "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #2563EB;");
        } catch (NumberFormatException e) {
            lblStockResultante.setText("—");
        }
    }

    public void setProducto(Producto p) {
        this.producto = p;
        lblProducto.setText(p.getNombre()
                + (p.getMarca() != null ? " — " + p.getMarca() : ""));
        lblStockActual.setText("Stock actual: " + p.getStock()
                + " unidades  |  Mínimo: " + p.getStockMinimo());
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {
        lblError.setText("");

        if (txtCantidad.getText().isBlank()) {
            lblError.setText("Ingresa la cantidad."); return;
        }
        if (cmbMotivo.getValue() == null) {
            lblError.setText("Selecciona el motivo del ajuste."); return;
        }

        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
            if (cantidad <= 0) {
                lblError.setText("La cantidad debe ser mayor a 0."); return;
            }
        } catch (NumberFormatException e) {
            lblError.setText("La cantidad debe ser un número entero."); return;
        }

        // Armar el motivo final
        String motivo;
        if ("Otra razón".equals(cmbMotivo.getValue())) {
            String especificacion = txtOtraRazon.getText().trim();
            if (especificacion.isEmpty()) {
                lblError.setText(
                        "Especifica el motivo cuando seleccionas 'Otra razón'."); return;
            }
            motivo = "Otra razón: " + especificacion;
        } else {
            motivo = cmbMotivo.getValue();
        }

        // Salida → cantidad negativa
        int cantidadFinal = rbEntrada.isSelected() ? cantidad : -cantidad;

        // Validar que no quede negativo
        int stockResultante = producto.getStock() + cantidadFinal;
        if (stockResultante < 0) {
            lblError.setText("No hay suficiente stock. "
                    + "Stock actual: " + producto.getStock()
                    + " — Máximo a retirar: " + producto.getStock()); return;
        }

        try {
            inventarioService.ajustarStock(
                    producto.getIdProducto(),
                    autenticacionService.getUsuarioActivo().getIdUsuario(),
                    cantidadFinal,
                    motivo);
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