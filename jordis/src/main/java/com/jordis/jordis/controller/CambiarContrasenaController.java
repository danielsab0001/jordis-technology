package com.jordis.jordis.controller;

import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.UsuarioService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CambiarContrasenaController {

    @FXML private Text          txtTitulo;
    @FXML private Label         lblUsuario;
    @FXML private PasswordField txtNueva;
    @FXML private PasswordField txtConfirmar;
    @FXML private Label         lblError;
    @FXML private Button        btnGuardar;

    private final UsuarioService usuarioService;

    private Usuario usuario;
    private Runnable onGuardado;

    public void setUsuario(Usuario u) {
        this.usuario = u;
        txtTitulo.setText("Cambiar contraseña");
        lblUsuario.setText("Usuario: " + u.getNombreCompleto()
                + "  |  Rol: " + u.getRol().name());
        lblError.setText("");
        txtNueva.clear();
        txtConfirmar.clear();
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {
        lblError.setText("");
        String nueva     = txtNueva.getText();
        String confirmar = txtConfirmar.getText();

        if (nueva.isBlank()) {
            lblError.setText("Ingresa la nueva contraseña."); return;
        }
        if (nueva.length() < 6) {
            lblError.setText("La contraseña debe tener al menos 6 caracteres."); return;
        }
        if (!nueva.equals(confirmar)) {
            lblError.setText("Las contraseñas no coinciden."); return;
        }

        try {
            usuarioService.cambiarContrasena(usuario.getIdUsuario(), nueva);
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