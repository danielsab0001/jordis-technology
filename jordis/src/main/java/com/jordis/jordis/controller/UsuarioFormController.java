package com.jordis.jordis.controller;

import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.UsuarioService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioFormController {

    @FXML private Text txtTitulo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private PasswordField txtContrasena;
    @FXML private VBox vboxContrasena;
    @FXML private ComboBox<Usuario.Rol> cmbRol;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    private final UsuarioService usuarioService;

    private Usuario usuarioEditar;
    private Runnable onGuardado;

    @FXML
    public void initialize() {
        cmbRol.getItems().addAll(Usuario.Rol.values());
        cmbRol.setValue(Usuario.Rol.CAJERO);
    }

    public void setUsuario(Usuario usuario) {
        this.usuarioEditar = usuario;
        if (usuario != null) {
            txtTitulo.setText("Editar Usuario");
            txtNombre.setText(usuario.getNombre());
            txtApellido.setText(usuario.getApellido());
            cmbRol.setValue(usuario.getRol());
            vboxContrasena.setVisible(false);
            vboxContrasena.setManaged(false);
        } else {
            txtTitulo.setText("Nuevo Usuario");
            vboxContrasena.setVisible(true);
            vboxContrasena.setManaged(true);
        }
    }

    public void setOnGuardado(Runnable callback) {
        this.onGuardado = callback;
    }

    @FXML
    public void onGuardar() {
        String nombre    = txtNombre.getText().trim();
        String apellido  = txtApellido.getText().trim();
        Usuario.Rol rol  = cmbRol.getValue();

        if (nombre.isEmpty() || apellido.isEmpty() || rol == null) {
            lblError.setText("Todos los campos son obligatorios.");
            return;
        }

        try {
            if (usuarioEditar == null) {
                String contrasena = txtContrasena.getText();
                if (contrasena.length() < 6) {
                    lblError.setText("La contraseña debe tener al menos 6 caracteres.");
                    return;
                }
                usuarioService.crear(nombre, apellido, contrasena, rol);
            } else {
                usuarioService.actualizar(usuarioEditar.getIdUsuario(), nombre, apellido, rol);
            }

            if (onGuardado != null) onGuardado.run();
            cerrar();

        } catch (UsuarioService.UsuarioYaExisteException e) {
            lblError.setText(e.getMessage());
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