package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.config.StageManager;
import com.jordis.jordis.model.AlertaSistema;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AlertaService;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.SesionService;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @FXML private Button btnCierreCaja;

    // Botones del menú lateral — administración
    @FXML private Button  btnCategorias;
    @FXML private Button  btnReportes;
    @FXML private Button  btnUsuarios;
    @FXML private Button  btnAuditoria;
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

    private final Set<Integer> alertasCriticasConocidas = new HashSet<>();

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
        btnAuditoria.setVisible(esAdmin);
        btnAuditoria.setManaged(esAdmin);
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
                btnCuentasPorPagar, btnCierreCaja, btnReportes, btnUsuarios,
                btnCategorias, btnConfiguracion, btnAlertas);

        botonesMenu.forEach(this::configurarAnimacionHover);

        if (esAdmin) {
            alertaService.escanearTodo();
            actualizarContadorAlertas();
            alertaService.getAlertasCriticas()
                    .forEach(a -> alertasCriticasConocidas.add(a.getIdAlerta()));
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

        javafx.animation.Timeline heartbeat = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(5), ev -> {
                    Usuario u = autenticacionService.getUsuarioActivo();
                    if (u != null) {
                        autenticacionService.actualizarHeartbeat(u.getIdUsuario());
                    }
                }));
        heartbeat.setCycleCount(javafx.animation.Animation.INDEFINITE);
        heartbeat.play();

        // Revisión proactiva de alertas: cada 2 minutos, re-escanea y
        // avisa con un mensaje emergente si apareció alguna alerta
        // crítica/alta nueva — sin que el admin tenga que entrar al
        // Centro de Alertas para enterarse. Solo aplica a administradores,
        // ya que son los únicos con acceso a ese módulo.
        if (esAdmin) {
            javafx.animation.Timeline revisionAlertas = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.minutes(2), ev -> {
                        alertaService.escanearTodo();
                        actualizarContadorAlertas();
                        revisarAlertasNuevas();
                    }));
            revisionAlertas.setCycleCount(javafx.animation.Animation.INDEFINITE);
            revisionAlertas.play();
        }
    }

    private void revisarAlertasNuevas() {
        List<AlertaSistema> criticas = alertaService.getAlertasCriticas();
        List<AlertaSistema> nuevas = criticas.stream()
                .filter(a -> !alertasCriticasConocidas.contains(a.getIdAlerta()))
                .toList();
        nuevas.forEach(a -> alertasCriticasConocidas.add(a.getIdAlerta()));

        if (nuevas.isEmpty()) return;

        String mensaje = nuevas.size() == 1
                ? nuevas.get(0).getTitulo()
                : nuevas.size() + " alertas críticas/altas nuevas";
        mostrarToastAlerta(mensaje);
    }

    // Aviso emergente discreto en la esquina inferior derecha, que
    // desaparece solo. Al hacer clic, lleva directo al Centro de Alertas.
    private void mostrarToastAlerta(String mensaje) {
        if (contenidoCentral.getScene() == null) return;
        Stage stage = (Stage) contenidoCentral.getScene().getWindow();
        if (stage == null) return;

        Popup popup = new Popup();

        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: #DC2626; -fx-background-radius: 8;"
                + " -fx-padding: 12 16; -fx-max-width: 300; -fx-cursor: hand;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 12, 0, 0, 4);");
        card.setCursor(Cursor.HAND);

        Label lTitulo = new Label("🔔  Nueva alerta");
        lTitulo.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12;");
        Label lMsg = new Label(mensaje);
        lMsg.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        lMsg.setWrapText(true);
        lMsg.setMaxWidth(270);
        card.getChildren().addAll(lTitulo, lMsg);

        card.setOnMouseClicked(e -> {
            popup.hide();
            onAlertas();
        });

        popup.getContent().add(card);
        popup.setAutoHide(true);
        popup.show(stage,
                stage.getX() + stage.getWidth() - 320,
                stage.getY() + stage.getHeight() - 110);

        // Se cierra sola a los 7 segundos, con un fundido suave
        javafx.animation.PauseTransition espera =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(7));
        espera.setOnFinished(ev -> {
            javafx.animation.FadeTransition fundido = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(400), card);
            fundido.setToValue(0);
            fundido.setOnFinished(ev2 -> popup.hide());
            fundido.play();
        });
        espera.play();
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
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(180), btnActivo);
            ft.setFromValue(0.55);
            ft.setToValue(1.0);
            ft.play();
        }
    }

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
    @FXML public void onCierreCaja() {cargarModulo("/fxml/cierre_caja.fxml", btnCierreCaja, "Cierre de Caja"); }
    @FXML public void onReportes() {
        cargarModulo("/fxml/reportes.fxml", btnReportes, "Módulo: Reportes");
    }
    @FXML public void onUsuarios() {
        cargarModulo("/fxml/usuarios.fxml", btnUsuarios, "Módulo: Usuarios");
    }
    @FXML public void onAuditoria() {cargarModulo("/fxml/auditoria.fxml", btnAuditoria, "Auditoría"); }
    @FXML public void onAlertas() {cargarModulo("/fxml/alertas.fxml", btnAlertas, "Centro de Alertas",(AlertaController c) -> c.setMainController(this)); }
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

    public void onCreditosVencidosFiltrado() {
        cargarModulo("/fxml/creditos.fxml", btnCreditos, "Módulo: Cuentas por Cobrar",
                (CreditoController c) -> c.filtrarVencidos());
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