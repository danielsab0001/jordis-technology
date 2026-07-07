package com.jordis.jordis.controller;

import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;

    private final AutenticacionService autenticacionService;
    private final StageManager stageManager;

    @FXML
    public void initialize() {
        // Permitir presionar Enter para iniciar sesión
        txtContrasena.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onIngresar();
            }
        });
        txtUsuario.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtContrasena.requestFocus();
            }
        });
    }

    @FXML
    public void onIngresar() {
        String usuario   = txtUsuario.getText().trim();
        String contrasena = txtContrasena.getText();

        if (usuario.isEmpty() || contrasena.isEmpty()) {
            mostrarError("Por favor complete todos los campos.");
            return;
        }

        lblError.setText("");
        btnIngresar.setDisable(true);

        try {
            Usuario usuarioAutenticado = autenticacionService.autenticar(usuario, contrasena);
            log.info("Acceso concedido a: {}", usuarioAutenticado.getNombreCompleto());
            stageManager.switchScene("/fxml/main.fxml", "Menú Principal");

        } catch (AutenticacionService.UsuarioBloqueadoException e) {
            // Mostrar mensaje pero NO bloquear el formulario
            mostrarError(e.getMessage());
            txtContrasena.clear();
            btnIngresar.setDisable(false);

        } catch (AutenticacionService.CuentaDesactivadaException e) {
            // Mostrar mensaje pero NO bloquear el formulario
            mostrarError(e.getMessage());
            txtContrasena.clear();
            btnIngresar.setDisable(false);

        } catch (AutenticacionService.CredencialesInvalidasException |
                 AutenticacionService.UsuarioNoEncontradoException e) {
            mostrarError(e.getMessage());
            txtContrasena.clear();
            txtContrasena.requestFocus();
            btnIngresar.setDisable(false);
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        btnIngresar.setDisable(false);
    }
}