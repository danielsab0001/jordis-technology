package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.UsuarioService;
import com.jordis.jordis.util.Paginador;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    @FXML private TableView<Usuario> tablaUsuarios;
    @FXML private TableColumn<Usuario, String> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colApellido;
    @FXML private TableColumn<Usuario, String> colNombreUsuario;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private TableColumn<Usuario, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final UsuarioService usuarioService;
    private final AutenticacionService autenticacionService;
    private final SpringFXMLLoader fxmlLoader;
    private Paginador<Usuario> paginador;

    @FXML
    public void initialize() {
        configurarColumnas();

        paginador = new Paginador<>(tablaUsuarios);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaUsuarios.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) -> filtrarUsuarios(val));

        cargarUsuarios();
    }

    private void filtrarUsuarios(String texto) {
        List<Usuario> base = usuarioService.obtenerTodos();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base);
            return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(u -> u.getNombre().toLowerCase().contains(t)
                        || u.getApellido().toLowerCase().contains(t)
                        || u.getNombreUsuario().toLowerCase().contains(t))
                .toList());
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdUsuario())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colApellido.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getApellido()));
        colNombreUsuario.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombreUsuario()));
        colRol.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getRol().name()));

        // Columna estado con badge de color
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                Usuario u = getTableView().getItems().get(getIndex());
                String texto; String colorFondo; String colorTexto;

                if (!u.getActivo()) {
                    texto = "Inactivo";
                    colorFondo = "#FEE2E2"; colorTexto = "#DC2626";
                } else if (u.getBloqueado()) {
                    texto = "Bloqueado";
                    colorFondo = "#FEF3C7"; colorTexto = "#B45309";
                } else {
                    texto = "Activo";
                    colorFondo = "#DCFCE7"; colorTexto = "#15803D";
                }

                Label badge = new Label(texto);
                badge.setStyle("-fx-background-color: " + colorFondo
                        + "; -fx-text-fill: " + colorTexto
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Usuario u = (Usuario) getTableRow().getItem();
                Usuario activo = autenticacionService.getUsuarioActivo();
                boolean esMiCuenta = activo != null &&
                        activo.getIdUsuario().equals(u.getIdUsuario());
                boolean estaActivo    = u.getActivo();
                boolean estaBloqueado = u.getBloqueado();

                MenuButton menu = new MenuButton("⋮ Acciones");
                menu.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10;"
                        + " -fx-padding: 3 8; -fx-cursor: hand;");

                MenuItem miEditar = new MenuItem("Editar");
                miEditar.setOnAction(e -> abrirFormulario(u));

                MenuItem miContrasena = new MenuItem("Cambiar contraseña");
                miContrasena.setOnAction(e -> abrirCambioContrasena(u));

                menu.getItems().addAll(miEditar, miContrasena);

                // Desbloquear: solo aparece si está bloqueado
                if (estaBloqueado && estaActivo) {
                    MenuItem miDesbloquear = new MenuItem("Desbloquear");
                    miDesbloquear.setOnAction(e -> desbloquear(u));
                    menu.getItems().add(miDesbloquear);
                }

                // Desactivar (deshabilitado si es mi propia cuenta) o Reactivar
                if (estaActivo) {
                    MenuItem miDesactivar = new MenuItem("Desactivar");
                    miDesactivar.setDisable(esMiCuenta);
                    miDesactivar.setOnAction(e -> desactivar(u));
                    menu.getItems().add(miDesactivar);
                } else {
                    MenuItem miReactivar = new MenuItem("Reactivar");
                    miReactivar.setOnAction(e -> reactivar(u));
                    menu.getItems().add(miReactivar);
                }

                setGraphic(menu);
            }
        });
    }

    private void cargarUsuarios() {
        paginador.setDatos(usuarioService.obtenerTodos());
    }

    // Filtra la tabla para mostrar únicamente el usuario indicado.
    // Se usa al navegar aquí desde el Centro de Alertas.
    public void filtrarPorId(Integer idUsuario) {
        if (idUsuario == null) return;
        Usuario u;
        try {
            u = usuarioService.obtenerPorId(idUsuario);
        } catch (Exception e) {
            mostrarMensaje("El usuario de la alerta ya no existe.", true);
            return;
        }
        txtBuscar.setText(u.getNombre());
        paginador.setDatos(List.of(u));
        mostrarMensaje("Mostrando: " + u.getNombreCompleto(), false);
    }

    @FXML public void onNuevoUsuario() { abrirFormulario(null); }

    @FXML
    public void onBuscar() {
        String texto = txtBuscar.getText().trim().toLowerCase();
        List<Usuario> todos = usuarioService.obtenerTodos();
        if (texto.isEmpty()) {
            tablaUsuarios.setItems(FXCollections.observableArrayList(todos));
        } else {
            List<Usuario> filtrados = todos.stream()
                    .filter(u -> u.getNombre().toLowerCase().contains(texto)
                            || u.getApellido().toLowerCase().contains(texto))
                    .toList();
            tablaUsuarios.setItems(FXCollections.observableArrayList(filtrados));
        }
    }

    @FXML
    public void onVerBloqueados() {
        tablaUsuarios.setItems(
                FXCollections.observableArrayList(usuarioService.obtenerBloqueados()));
        mostrarMensaje("Mostrando usuarios bloqueados.", false);
    }

    @FXML public void onVerTodos() {
        txtBuscar.clear();
        lblMensaje.setText("");
    }

    private void abrirCambioContrasena(Usuario usuario) {
        try {
            SpringFXMLLoader.LoadResult<CambiarContrasenaController> result =
                    fxmlLoader.loadWithController("/fxml/cambiar_contrasena_form.fxml");
            result.controller.setUsuario(usuario);
            result.controller.setOnGuardado(() ->
                    mostrarMensaje("Contraseña actualizada correctamente.", false));
            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Cambiar contraseña — " + usuario.getNombreCompleto(), 400, 330);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo cambio de contraseña", e);
        }
    }

    private void desbloquear(Usuario usuario) {
        confirmar("¿Desbloquear a " + usuario.getNombreCompleto() + "?",
                "Confirmar desbloqueo", () -> {
                    usuarioService.desbloquear(usuario.getIdUsuario());
                    cargarUsuarios();
                    mostrarMensaje("Usuario desbloqueado correctamente.", false);
                });
    }

    private void desactivar(Usuario usuario) {
        confirmar("¿Desactivar a " + usuario.getNombreCompleto()
                        + "? No podrá iniciar sesión hasta que sea reactivado.",
                "Confirmar desactivación", () -> {
                    usuarioService.desactivar(usuario.getIdUsuario());
                    cargarUsuarios();
                    mostrarMensaje("Usuario desactivado.", false);
                });
    }

    private void reactivar(Usuario usuario) {
        confirmar("¿Reactivar a " + usuario.getNombreCompleto()
                        + "? Podrá volver a iniciar sesión.",
                "Confirmar reactivación", () -> {
                    usuarioService.reactivar(usuario.getIdUsuario());
                    cargarUsuarios();
                    mostrarMensaje("Usuario reactivado correctamente.", false);
                });
    }

    private void confirmar(String mensaje, String titulo, Runnable accion) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                mensaje, ButtonType.YES, ButtonType.NO);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) accion.run();
        });
    }

    private void abrirFormulario(Usuario usuario) {
        try {
            SpringFXMLLoader.LoadResult<UsuarioFormController> result =
                    fxmlLoader.loadWithController("/fxml/usuario_form.fxml");

            result.controller.setUsuario(usuario);
            result.controller.setOnGuardado(() -> {
                cargarUsuarios();
                mostrarMensaje("Usuario guardado correctamente.", false);
            });

            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, usuario == null ? "Nuevo Usuario" : "Editar Usuario", 400, 480);
            stage.showAndWait();

        } catch (Exception e) {
            log.error("Error abriendo formulario de usuario", e);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}