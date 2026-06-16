package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    // ---- Elementos del FXML ----
    @FXML private Label     lblBienvenida;
    @FXML private Label     lblRol;
    @FXML private Label     lblEstado;
    @FXML private AnchorPane contenidoCentral;

    // Botones del menú lateral
    @FXML private Button btnVentas;
    @FXML private Button btnClientes;
    @FXML private Button btnProductos;
    @FXML private Button btnInventario;
    @FXML private Button btnProveedores;
    @FXML private Button btnReportes;
    @FXML private Button btnUsuarios;

    // ---- Dependencias ----
    private final AutenticacionService autenticacionService;
    private final StageManager stageManager;
    private final SpringFXMLLoader fxmlLoader;

    // Botón activo actualmente
    private Button btnActivo;

    // ---- Inicialización ----
    @FXML
    public void initialize() {
        Usuario usuario = autenticacionService.getUsuarioActivo();

        if (usuario == null) {
            stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
            return;
        }

        // Personalizar barra superior
        lblBienvenida.setText("Hola, " + usuario.getNombre());
        lblRol.setText(usuario.getRol().name());

        // Control de acceso por rol
        boolean esAdmin = usuario.getRol() == Usuario.Rol.ADMINISTRADOR;
        btnReportes.setVisible(esAdmin);
        btnReportes.setManaged(esAdmin);
        btnUsuarios.setVisible(esAdmin);
        btnUsuarios.setManaged(esAdmin);
        btnProveedores.setVisible(esAdmin);
        btnProveedores.setManaged(esAdmin);

        // Pantalla inicial: Ventas para cajero, dashboard vacío para admin
        if (esAdmin) {
            lblEstado.setText("Bienvenido, " + usuario.getNombreCompleto());
        } else {
            onVentas();
        }
    }

    // ---- Navegación entre módulos ----

    private void cargarModulo(String fxml, Button boton, String etiqueta) {
        try {
            Parent vista = fxmlLoader.load(fxml);
            contenidoCentral.getChildren().setAll(vista);
            AnchorPane.setTopAnchor(vista, 0.0);
            AnchorPane.setBottomAnchor(vista, 0.0);
            AnchorPane.setLeftAnchor(vista, 0.0);
            AnchorPane.setRightAnchor(vista, 0.0);
            lblEstado.setText(etiqueta);
            marcarBotonActivo(boton);
            log.debug("Módulo cargado: {}", fxml);
        } catch (Exception e) {
            lblEstado.setText("");
            log.error("{}: {}", fxml, e.getMessage());
        }
    }

    private void marcarBotonActivo(Button boton) {
        // Quitar resaltado del botón anterior
        if (btnActivo != null) {
            btnActivo.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: white;" +
                            "-fx-font-size: 13; -fx-alignment: CENTER_LEFT;" +
                            "-fx-cursor: hand; -fx-padding: 0 0 0 16;");
        }
        // Resaltar el nuevo botón activo
        btnActivo = boton;
        if (btnActivo != null) {
            btnActivo.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;" +
                            "-fx-font-size: 13; -fx-alignment: CENTER_LEFT;" +
                            "-fx-cursor: hand; -fx-padding: 0 0 0 16;" +
                            "-fx-border-color: transparent transparent transparent white;" +
                            "-fx-border-width: 0 0 0 3;");
        }
    }

    // ---- Handlers del menú lateral ----

    @FXML public void onVentas()      { cargarModulo("/fxml/ventas.fxml",      btnVentas,      "Módulo: Ventas"); }
    @FXML public void onClientes()    { cargarModulo("/fxml/clientes.fxml",    btnClientes,    "Módulo: Clientes"); }
    @FXML public void onProductos()   { cargarModulo("/fxml/productos.fxml",   btnProductos,   "Módulo: Productos"); }
    @FXML public void onInventario()  { cargarModulo("/fxml/inventario.fxml",  btnInventario,  "Módulo: Inventario"); }
    @FXML public void onProveedores() { cargarModulo("/fxml/proveedores.fxml", btnProveedores, "Módulo: Proveedores"); }
    @FXML public void onReportes()    { cargarModulo("/fxml/reportes.fxml",    btnReportes,    "Módulo: Reportes"); }
    @FXML public void onUsuarios()    { cargarModulo("/fxml/usuarios.fxml",    btnUsuarios,    "Módulo: Usuarios"); }
    @FXML public void onCompras()     { cargarModulo("/fxml/compras.fxml",     null,           "Módulo: Compras"); }

    // ---- Cerrar sesión ----

    @FXML
    public void onCerrarSesion() {
        autenticacionService.cerrarSesion();
        stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
    }
}