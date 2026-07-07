package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.UsuarioService;
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
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private TableColumn<Usuario, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final UsuarioService usuarioService;
    private final AutenticacionService autenticacionService;
    private final SpringFXMLLoader fxmlLoader;

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarUsuarios();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdUsuario())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colApellido.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getApellido()));
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
            private final Button btnEditar      = crearBtn("Editar",      "#2563EB", "#EFF6FF");
            private final Button btnContrasena = crearBtn("Contraseña", "#6D28D9", "#EDE9FE");
            private final Button btnDesbloquear = crearBtn("Desbloquear", "#B45309", "#FEF3C7");
            private final Button btnDesactivar  = crearBtn("Desactivar",  "#64748B", "#F1F5F9");
            private final Button btnReactivar   = crearBtn("Reactivar",   "#15803D", "#DCFCE7");
            private final HBox box = new HBox(5,
                    btnEditar, btnContrasena, btnDesbloquear, btnDesactivar, btnReactivar);

            {
                btnEditar.setOnAction(e ->
                        abrirFormulario(getTableView().getItems().get(getIndex())));
                btnDesbloquear.setOnAction(e ->
                        desbloquear(getTableView().getItems().get(getIndex())));
                btnDesactivar.setOnAction(e ->
                        desactivar(getTableView().getItems().get(getIndex())));
                btnReactivar.setOnAction(e ->
                        reactivar(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }

                Usuario u      = getTableView().getItems().get(getIndex());
                Usuario activo = autenticacionService.getUsuarioActivo();
                boolean esMiCuenta = activo != null &&
                        activo.getIdUsuario().equals(u.getIdUsuario());
                boolean estaActivo   = u.getActivo();
                boolean estaBloqueado = u.getBloqueado();

                btnContrasena.setOnAction(e -> abrirCambioContrasena(u));

                // Desbloquear: solo si está bloqueado
                btnDesbloquear.setVisible(estaBloqueado && estaActivo);
                btnDesbloquear.setManaged(estaBloqueado && estaActivo);

                // Desactivar: solo si está activo y no es mi cuenta
                btnDesactivar.setVisible(estaActivo);
                btnDesactivar.setManaged(estaActivo);
                btnDesactivar.setDisable(esMiCuenta);
                btnDesactivar.setStyle(crearEstiloBtn(
                        esMiCuenta ? "#CBD5E1" : "#64748B",
                        esMiCuenta ? "#F8FAFC" : "#F1F5F9"
                ));

                // Reactivar: solo si está inactivo
                btnReactivar.setVisible(!estaActivo);
                btnReactivar.setManaged(!estaActivo);

                setGraphic(box);
            }
        });
    }

    private String crearEstiloBtn(String colorTexto, String colorFondo) {
        return "-fx-background-color: " + colorFondo
                + "; -fx-text-fill: " + colorTexto
                + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 8; -fx-cursor: hand;";
    }

    private Button crearBtn(String texto, String colorTexto, String colorFondo) {
        Button btn = new Button(texto);
        btn.setStyle(crearEstiloBtn(colorTexto, colorFondo));
        return btn;
    }

    private void cargarUsuarios() {
        tablaUsuarios.setItems(
                FXCollections.observableArrayList(usuarioService.obtenerTodos()));
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

    @FXML
    public void onVerTodos() {
        cargarUsuarios();
        lblMensaje.setText("");
    }

    private void abrirCambioContrasena(Usuario usuario) {
        try {
            SpringFXMLLoader.LoadResult<CambiarContrasenaController> result =
                    fxmlLoader.loadWithController("/fxml/cambiar_contrasena_form.fxml");
            result.controller.setUsuario(usuario);
            result.controller.setOnGuardado(() ->
                    mostrarMensaje("Contraseña actualizada correctamente.", false));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Cambiar contraseña — " + usuario.getNombreCompleto());
            stage.setScene(new Scene(result.root, 440, 320));
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

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(usuario == null ? "Nuevo Usuario" : "Editar Usuario");
            stage.setScene(new Scene(result.root, 440, 420));
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