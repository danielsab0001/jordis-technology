package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AlertaService;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.SesionService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
    @FXML private Button  btnCategorias;
    @FXML private Button  btnReportes;
    @FXML private Button  btnUsuarios;
    @FXML private Button  btnConfiguracion;
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

        List<Button> botonesMenu = List.of(
                btnDashboard, btnVentas, btnClientes, btnProductos,
                btnInventario, btnCompras, btnProveedores, btnCreditos,
                btnCuentasPorPagar, btnReportes, btnUsuarios,
                btnCategorias, btnConfiguracion, btnAlertas);

        botonesMenu.forEach(this::configurarAnimacionHover);

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
        cargarModulo(fxml, boton, etiqueta, null);
    }

    // Variante que además da acceso al controller recién cargado, para
    // poder aplicarle un filtro (p. ej. al navegar desde una alerta).
    private <C> void cargarModulo(String fxml, Button boton, String etiqueta,
                                  java.util.function.Consumer<C> configurador) {
        try {
            SpringFXMLLoader.LoadResult<C> result = fxmlLoader.loadWithController(fxml);
            if (configurador != null) {
                configurador.accept(result.controller);
            }
            contenidoCentral.getChildren().setAll(result.root);
            AnchorPane.setTopAnchor(result.root, 0.0);
            AnchorPane.setBottomAnchor(result.root, 0.0);
            AnchorPane.setLeftAnchor(result.root, 0.0);
            AnchorPane.setRightAnchor(result.root, 0.0);
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

    // Clase CSS que marca el botón del módulo activo (ver /css/sidebar.css)
    private static final String CLASE_BTN_ACTIVO = "sidebar-button-active";

    private void marcarBotonActivo(Button boton) {
        if (btnActivo != null) {
            btnActivo.getStyleClass().remove(CLASE_BTN_ACTIVO);
            btnActivo.setScaleX(1.0);
            btnActivo.setScaleY(1.0);
        }
        btnActivo = boton;
        if (btnActivo != null) {
            btnActivo.getStyleClass().add(CLASE_BTN_ACTIVO);
            // Pequeña animación de aparición al seleccionar el módulo (~180ms)
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(180), btnActivo);
            ft.setFromValue(0.55);
            ft.setToValue(1.0);
            ft.play();
        }
    }

    // Pequeño efecto de "hover" (escala sutil, ~160ms) para los botones
    // del menú lateral. Los colores del estado normal/hover/activo viven
    // en /css/sidebar.css (clases "sidebar-button" y "sidebar-button-active").
    private void configurarAnimacionHover(Button btn) {
        btn.setOnMouseEntered(e -> {
            if (btn == btnActivo) return;
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
                    javafx.util.Duration.millis(160), btn);
            st.setToX(1.015);
            st.setToY(1.015);
            st.play();
        });
        btn.setOnMouseExited(e -> {
            if (btn == btnActivo) return;
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
                    javafx.util.Duration.millis(160), btn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
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
        cargarModulo("/fxml/creditos.fxml", btnCreditos, "Módulo: Cuentas por Cobrar");
    }
    @FXML public void onCuentasPorPagar() {cargarModulo("/fxml/cuentas_por_pagar.fxml", btnCuentasPorPagar, "Módulo: Cuentas por Pagar"); }
    @FXML public void onReportes() {
        cargarModulo("/fxml/reportes.fxml", btnReportes, "Módulo: Reportes");
    }
    @FXML public void onUsuarios() {
        cargarModulo("/fxml/usuarios.fxml", btnUsuarios, "Módulo: Usuarios");
    }
    @FXML public void onAlertas() {
        cargarModulo("/fxml/alertas.fxml", btnAlertas, "Centro de Alertas",
                (AlertaController c) -> c.setMainController(this));
    }
    @FXML public void onCategorias() {cargarModulo("/fxml/categorias.fxml", btnCategorias, "Módulo: Categorías"); }
    @FXML public void onConfiguracion() {cargarModulo("/fxml/configuracion.fxml", btnConfiguracion, "Configuración"); }

    // ---- Navegación filtrada (usada desde el Centro de Alertas) ----

    public void onInventarioFiltrado(Integer idProducto) {
        cargarModulo("/fxml/inventario.fxml", btnInventario, "Módulo: Inventario",
                (InventarioController c) -> c.filtrarPorProductoId(idProducto));
    }

    public void onUsuariosFiltrado(Integer idUsuario) {
        cargarModulo("/fxml/usuarios.fxml", btnUsuarios, "Módulo: Usuarios",
                (UsuarioController c) -> c.filtrarPorId(idUsuario));
    }

    public void onCreditosFiltrado(Integer idVenta) {
        cargarModulo("/fxml/creditos.fxml", btnCreditos, "Módulo: Cuentas por Cobrar",
                (CreditoController c) -> c.filtrarPorVenta(idVenta));
    }

    public void onCuentasPorPagarFiltrado(Integer idCuenta) {
        cargarModulo("/fxml/cuentas_por_pagar.fxml", btnCuentasPorPagar, "Módulo: Cuentas por Pagar",
                (CuentaPorPagarController c) -> c.filtrarPorId(idCuenta));
    }

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