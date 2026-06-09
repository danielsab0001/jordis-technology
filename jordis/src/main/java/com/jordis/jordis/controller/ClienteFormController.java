package com.jordis.jordis.controller;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.service.ClienteService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClienteFormController {

    @FXML private Text txtTitulo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtCedula;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;
    @FXML private Label lblCedulaError;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final ClienteService clienteService;

    private Cliente clienteEditar;
    private Runnable onGuardado;

    public void setCliente(Cliente cliente) {
        this.clienteEditar = cliente;
        if (cliente != null) {
            txtTitulo.setText("Editar Cliente");
            txtNombre.setText(cliente.getNombre());
            txtApellido.setText(cliente.getApellido());
            txtCedula.setText(cliente.getCedulaIdentificacion());
            txtTelefono.setText(cliente.getTelefono() != null ? cliente.getTelefono() : "");
            txtDireccion.setText(cliente.getDireccion() != null ? cliente.getDireccion() : "");
        } else {
            txtTitulo.setText("Nuevo Cliente");
        }

        // Validación en tiempo real de cédula
        txtCedula.textProperty().addListener((obs, old, nuevo) -> {
            lblCedulaError.setText("");
            lblError.setText("");
        });
    }

    public void setOnGuardado(Runnable callback) {
        this.onGuardado = callback;
    }

    @FXML
    public void onGuardar() {
        String nombre    = txtNombre.getText().trim();
        String apellido  = txtApellido.getText().trim();
        String cedula    = txtCedula.getText().trim();
        String telefono  = txtTelefono.getText().trim();
        String direccion = txtDireccion.getText().trim();

        if (nombre.isEmpty() || apellido.isEmpty() || cedula.isEmpty()) {
            lblError.setText("Nombre, apellido y cédula son obligatorios.");
            return;
        }

        try {
            if (clienteEditar == null) {
                clienteService.crear(nombre, apellido, cedula, telefono, direccion);
            } else {
                clienteService.actualizar(clienteEditar.getIdCliente(),
                        nombre, apellido, cedula, telefono, direccion);
            }
            if (onGuardado != null) onGuardado.run();
            cerrar();

        } catch (ClienteService.CedulaDuplicadaException e) {
            lblCedulaError.setText(e.getMessage());
        } catch (Exception e) {
            lblError.setText("Error inesperado: " + e.getMessage());
        }
    }

    @FXML
    public void onCancelar() {
        cerrar();
    }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }
}