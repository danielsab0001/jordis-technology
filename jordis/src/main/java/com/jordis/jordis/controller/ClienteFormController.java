package com.jordis.jordis.controller;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.service.ClienteService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClienteFormController {

    @FXML private Text txtTitulo;
    @FXML private RadioButton rbPersona;
    @FXML private RadioButton rbEmpresa;
    @FXML private VBox panelPersona;
    @FXML private VBox panelEmpresa;

    // Campos persona
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtCedula;

    // Campos empresa
    @FXML private TextField txtRazonSocial;
    @FXML private TextField txtRnc;
    @FXML private TextField txtContactoPrincipal;

    // Campos comunes
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final ClienteService clienteService;
    private Cliente clienteEditar;
    private Runnable onGuardado;

    @FXML
    public void initialize() {
        // Cambiar paneles al seleccionar tipo
        rbPersona.selectedProperty().addListener((obs, old, val) -> {
            panelPersona.setVisible(val);
            panelPersona.setManaged(val);
            panelEmpresa.setVisible(!val);
            panelEmpresa.setManaged(!val);
        });
    }

    public void setCliente(Cliente cliente) {
        this.clienteEditar = cliente;
        if (cliente == null) {
            txtTitulo.setText("Nuevo Cliente");
            return;
        }

        txtTitulo.setText("Editar Cliente");
        txtTelefono.setText(nvl(cliente.getTelefono()));
        txtDireccion.setText(nvl(cliente.getDireccion()));

        if (cliente.esEmpresa()) {
            rbEmpresa.setSelected(true);
            txtRazonSocial.setText(nvl(cliente.getRazonSocial()));
            txtRnc.setText(nvl(cliente.getRnc()));
            txtContactoPrincipal.setText(nvl(cliente.getContactoPrincipal()));
        } else {
            rbPersona.setSelected(true);
            txtNombre.setText(nvl(cliente.getNombre()));
            txtApellido.setText(nvl(cliente.getApellido()));
            txtCedula.setText(nvl(cliente.getCedulaIdentificacion()));
        }
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {
        lblError.setText("");
        boolean esEmpresa = rbEmpresa.isSelected();

        try {
            if (clienteEditar == null) {
                if (esEmpresa) {
                    validarNoVacio(txtRazonSocial, "La razón social es obligatoria.");
                    validarNoVacio(txtRnc, "El RNC es obligatorio.");
                    clienteService.crearEmpresa(
                            txtRazonSocial.getText().trim(),
                            txtRnc.getText().trim(),
                            txtContactoPrincipal.getText().trim(),
                            txtTelefono.getText().trim(),
                            txtDireccion.getText().trim());
                } else {
                    validarNoVacio(txtNombre, "El nombre es obligatorio.");
                    validarNoVacio(txtCedula, "La cédula es obligatoria.");
                    clienteService.crear(
                            txtNombre.getText().trim(),
                            txtApellido.getText().trim(),
                            txtCedula.getText().trim(),
                            txtTelefono.getText().trim(),
                            txtDireccion.getText().trim());
                }
            } else {
                clienteService.actualizar(
                        clienteEditar.getIdCliente(),
                        esEmpresa ? txtRazonSocial.getText().trim() : txtNombre.getText().trim(),
                        esEmpresa ? null : txtApellido.getText().trim(),
                        esEmpresa ? txtRnc.getText().trim() : txtCedula.getText().trim(),
                        txtTelefono.getText().trim(),
                        txtDireccion.getText().trim(),
                        esEmpresa ? txtContactoPrincipal.getText().trim() : null,
                        esEmpresa ? "EMPRESA" : "PERSONA");
            }
            if (onGuardado != null) onGuardado.run();
            cerrar();

        } catch (ClienteService.CedulaDuplicadaException |
                 ClienteService.RncDuplicadoException e) {
            lblError.setText(e.getMessage());
        } catch (ValidacionException e) {
            lblError.setText(e.getMessage());
        } catch (Exception e) {
            lblError.setText("Error inesperado: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }

    private void validarNoVacio(TextField campo, String mensaje) {
        if (campo.getText() == null || campo.getText().trim().isEmpty()) {
            throw new ValidacionException(mensaje);
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }

    static class ValidacionException extends RuntimeException {
        ValidacionException(String msg) { super(msg); }
    }
}