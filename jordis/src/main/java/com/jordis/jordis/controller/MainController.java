package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AlertaService;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.SesionService;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    // ---- Elementos del FXML ----
    @FXML private Label      lblBienvenida;
    @FXML private Label      lblRol;
    @FXML private Label      lblEstado;
    @FXML private AnchorPane contenidoCentral;

    // Botones del menú lateral — módulos
    @FXML private Button btnDashboard;
    @FXML private Button btnVentas;
    @FXML private Button btnClientes;
    @FXML private Button btnProductos;
    @FXML private Button btnInventario;
    @FXML private Button btnCompras;
    @FXML private Button btnProveedores;
    @FXML private Button btnCreditos;
    @FXML private Button btnCuentasPorPagar;

    // Botones del menú lateral — administración
    @FXML private Button btnCategorias;
    @FXML private Button  btnReportes;
    @FXML private Button  btnUsuarios;
    @FXML private Button btnConfiguracion;
    @FXML private Button  btnAlertas;

    // Sección y contador de alertas
    @FXML private Label    lblSeccionAdmin;
    @FXML private Label    lblAlertaContador;

    // ---- Dependencias ----
    private final AutenticacionService autenticacionService;
    private final StageManager         stageManager;
    private final SpringFXMLLoader     fxmlLoader;
    private final AlertaService        alertaService;
    private final SesionService sesionService;

    private Button btnActivo;

    // ---- Inicialización ----
    @FXML
    public void initialize() {
        Usuario usuario = autenticacionService.getUsuarioActivo();
        if (usuario == null) {
            stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
            return;
        }

        lblBienvenida.setText("Hola, " + usuario.getNombre());
        lblRol.setText(usuario.getRol().name());

        boolean esAdmin = usuario.getRol() == Usuario.Rol.ADMINISTRADOR;

        // Ocultar sección de administración completa para cajeros
        lblSeccionAdmin.setVisible(esAdmin);
        lblSeccionAdmin.setManaged(esAdmin);
        btnCategorias.setVisible(esAdmin);
        btnCategorias.setManaged(esAdmin);
        btnReportes.setVisible(esAdmin);
        btnReportes.setManaged(esAdmin);
        btnUsuarios.setVisible(esAdmin);
        btnUsuarios.setManaged(esAdmin);
        btnConfiguracion.setVisible(esAdmin);
        btnConfiguracion.setManaged(esAdmin);
        btnAlertas.setVisible(esAdmin);
        btnAlertas.setManaged(esAdmin);
        btnAlertas.setVisible(esAdmin);
        btnAlertas.setManaged(esAdmin);
        lblAlertaContador.setVisible(false);

        if (esAdmin) {
            alertaService.escanearTodo();
            actualizarContadorAlertas();
            onDashboard();
        } else {
            onVentas();
        }
        sesionService.iniciarTimer(() -> {
            // Ejecutar en el hilo de JavaFX
            javafx.application.Platform.runLater(() -> {
                autenticacionService.cerrarSesion();
                sesionService.detenerTimer();
                stageManager.switchScene("/fxml/login.fxml",
                        "Sesión expirada — Iniciar sesión");
            });
        });
    }

    // ---- Navegación ----

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
            // Actualizar contador cada vez que se navega
            Usuario usuario = autenticacionService.getUsuarioActivo();
            if (usuario != null && usuario.getRol() == Usuario.Rol.ADMINISTRADOR) {
                actualizarContadorAlertas();
            }
            log.debug("Módulo cargado: {}", fxml);
        } catch (Exception e) {
            lblEstado.setText("Error cargando módulo.");
            log.error("Error cargando módulo {}", fxml, e);
        }
    }

    private void marcarBotonActivo(Button boton) {
        // Quitar resaltado del anterior
        if (btnActivo != null) {
            btnActivo.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: white;"
                            + "-fx-font-size: 13; -fx-alignment: CENTER_LEFT;"
                            + "-fx-cursor: hand; -fx-padding: 0 0 0 16;");
        }
        btnActivo = boton;
        if (btnActivo != null) {
            btnActivo.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;"
                            + "-fx-font-size: 13; -fx-alignment: CENTER_LEFT;"
                            + "-fx-cursor: hand; -fx-padding: 0 0 0 16;"
                            + "-fx-border-color: transparent transparent transparent white;"
                            + "-fx-border-width: 0 0 0 3;");
        }
    }

    public void actualizarContadorAlertas() {
        long noLeidas = alertaService.contarNoLeidas();
        if (noLeidas > 0) {
            lblAlertaContador.setText(String.valueOf(noLeidas));
            lblAlertaContador.setVisible(true);
        } else {
            lblAlertaContador.setVisible(false);
        }
    }

    // ---- Handlers ----

    @FXML public void onVentas() {
        cargarModulo("/fxml/ventas.fxml", btnVentas, "Módulo: Ventas");
    }
    @FXML public void onClientes() {
        cargarModulo("/fxml/clientes.fxml", btnClientes, "Módulo: Clientes");
    }
    @FXML public void onProductos() {
        cargarModulo("/fxml/productos.fxml", btnProductos, "Módulo: Productos");
    }
    @FXML public void onInventario() {
        cargarModulo("/fxml/inventario.fxml", btnInventario, "Módulo: Inventario");
    }
    @FXML public void onCompras() {
        cargarModulo("/fxml/compras.fxml", btnCompras, "Módulo: Compras");
    }
    @FXML public void onProveedores() {
        cargarModulo("/fxml/proveedores.fxml", btnProveedores, "Módulo: Proveedores");
    }
    @FXML public void onCreditos() {
        cargarModulo("/fxml/creditos.fxml", btnCreditos, "Módulo: Créditos");
    }
    @FXML public void onCuentasPorPagar() {cargarModulo("/fxml/cuentas_por_pagar.fxml", btnCuentasPorPagar, "Módulo: Cuentas por Pagar"); }
    @FXML public void onReportes() {
        cargarModulo("/fxml/reportes.fxml", btnReportes, "Módulo: Reportes");
    }
    @FXML public void onUsuarios() {
        cargarModulo("/fxml/usuarios.fxml", btnUsuarios, "Módulo: Usuarios");
    }
    @FXML public void onAlertas() {
        cargarModulo("/fxml/alertas.fxml", btnAlertas, "Centro de Alertas");
    }
    @FXML public void onCategorias() {cargarModulo("/fxml/categorias.fxml", btnCategorias, "Módulo: Categorías"); }
    @FXML public void onConfiguracion() {cargarModulo("/fxml/configuracion.fxml", btnConfiguracion, "Configuración"); }

    @FXML
    public void onCerrarSesion() {
        sesionService.detenerTimer();
        autenticacionService.cerrarSesion();
        stageManager.switchScene("/fxml/login.fxml", "Iniciar sesión");
    }

    @FXML public void onDashboard() {
        try {
            SpringFXMLLoader.LoadResult<DashboardController> result =
                    fxmlLoader.loadWithController("/fxml/dashboard.fxml");
            result.controller.setMainController(this);
            contenidoCentral.getChildren().setAll(result.root);
            AnchorPane.setTopAnchor(result.root, 0.0);
            AnchorPane.setBottomAnchor(result.root, 0.0);
            AnchorPane.setLeftAnchor(result.root, 0.0);
            AnchorPane.setRightAnchor(result.root, 0.0);
            lblEstado.setText("Dashboard");
            marcarBotonActivo(btnDashboard);
        } catch (Exception e) {
            log.error("Error cargando dashboard", e);
        }
    }
}