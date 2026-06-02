package com.jordis.jordis.controller;

import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    @FXML private Label lblBienvenida;
    @FXML private Label lblRol;
    @FXML private Text txtBienvenidaCentro;

    // Botones de administración (solo visibles para ADMIN)
    @FXML private Button btnReportes;
    @FXML private Button btnUsuarios;
    @FXML private Button btnProveedores;

    private final AutenticacionService autenticacionService;
    private final StageManager stageManager;

    @FXML
    public void initialize() {
        Usuario usuario = autenticacionService.getUsuarioActivo();
        if (usuario == null) {
            stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
            return;
        }

        lblBienvenida.setText("Hola, " + usuario.getNombre());
        lblRol.setText(usuario.getRol().name());
        txtBienvenidaCentro.setText("Bienvenido, " + usuario.getNombreCompleto());

        // Control de acceso por rol: ocultar módulos de admin al cajero
        boolean esAdmin = usuario.getRol() == Usuario.Rol.ADMINISTRADOR;
        btnReportes.setVisible(esAdmin);
        btnUsuarios.setVisible(esAdmin);
        btnProveedores.setVisible(esAdmin);
    }

    @FXML public void onVentas()      { log.info("Módulo: Ventas"); }
    @FXML public void onClientes()    { log.info("Módulo: Clientes"); }
    @FXML public void onProductos()   { log.info("Módulo: Productos"); }
    @FXML public void onInventario()  { log.info("Módulo: Inventario"); }
    @FXML public void onProveedores() { log.info("Módulo: Proveedores"); }
    @FXML public void onReportes()    { log.info("Módulo: Reportes"); }
    @FXML public void onUsuarios()    { log.info("Módulo: Usuarios"); }

    @FXML
    public void onCerrarSesion() {
        autenticacionService.cerrarSesion();
        stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
    }
}