package com.jordis.jordis.controller;

import com.jordis.jordis.model.Proveedor;
import com.jordis.jordis.service.ProveedorService;
import com.jordis.jordis.util.DominicanoValidador;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProveedorFormController {

    @FXML private Text txtTitulo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtContacto;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtDireccion;
    @FXML private TextArea  txtDescripcion;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final ProveedorService proveedorService;
    private Proveedor proveedorEditar;
    private Runnable onGuardado;

    public void setProveedor(Proveedor proveedor) {
        this.proveedorEditar = proveedor;
        if (proveedor != null) {
            txtTitulo.setText("Editar Proveedor");
            txtNombre.setText(proveedor.getNombre());
            txtContacto.setText(nvl(proveedor.getContacto()));
            txtTelefono.setText(nvl(proveedor.getTelefono()));
            txtCorreo.setText(nvl(proveedor.getCorreo()));
            txtDireccion.setText(nvl(proveedor.getDireccion()));
            txtDescripcion.setText(nvl(proveedor.getDescripcion()));
        } else {
            txtTitulo.setText("Nuevo Proveedor");
        }
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {

        String telefono = txtTelefono.getText() != null ? txtTelefono.getText().trim() : "";
        if (!telefono.isEmpty() && !DominicanoValidador.esTelefonoValido(telefono)) {
            lblError.setText("El teléfono debe tener 10 dígitos con código de "
                    + "área 809, 829 u 849 (ej: 809-555-1234).");
            return;
        }

        String correo = txtCorreo.getText() != null ? txtCorreo.getText().trim() : "";
        if (!correo.isEmpty() && !DominicanoValidador.esCorreoValido(correo)) {
            lblError.setText("El correo electrónico no tiene un formato válido.");
            return;
        }

        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            lblError.setText("El nombre del proveedor es obligatorio.");
            return;
        }
        try {
            if (proveedorEditar == null) {
                proveedorService.crear(
                        nombre,
                        txtContacto.getText().trim(),
                        txtTelefono.getText().trim(),
                        txtCorreo.getText().trim(),
                        txtDireccion.getText().trim(),
                        txtDescripcion.getText().trim());
            } else {
                proveedorService.actualizar(
                        proveedorEditar.getIdProveedor(),
                        nombre,
                        txtContacto.getText().trim(),
                        txtTelefono.getText().trim(),
                        txtCorreo.getText().trim(),
                        txtDireccion.getText().trim(),
                        txtDescripcion.getText().trim());
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

    private String nvl(String s) { return s != null ? s : ""; }
}