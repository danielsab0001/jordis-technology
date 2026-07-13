package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Proveedor;
import com.jordis.jordis.service.ProveedorService;
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
public class ProveedorController {

    @FXML private TableView<Proveedor> tablaProveedores;
    @FXML private TableColumn<Proveedor, String> colId;
    @FXML private TableColumn<Proveedor, String> colNombre;
    @FXML private TableColumn<Proveedor, String> colContacto;
    @FXML private TableColumn<Proveedor, String> colTelefono;
    @FXML private TableColumn<Proveedor, String> colEstado;
    @FXML private TableColumn<Proveedor, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final ProveedorService proveedorService;
    private final SpringFXMLLoader fxmlLoader;
    private Paginador<Proveedor> paginador;

    @FXML
    public void initialize() {
        configurarColumnas();
        paginador = new Paginador<>(tablaProveedores);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaProveedores.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) ->
                filtrarProveedores(val));

        cargarProveedores();
    }

    private void filtrarProveedores(String texto) {
        List<Proveedor> base =
                proveedorService.obtenerTodosIncluyendoInactivos();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base);
            return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(p -> p.getNombre().toLowerCase().contains(t)
                        || (p.getContacto() != null
                        && p.getContacto().toLowerCase().contains(t))
                        || (p.getTelefono() != null
                        && p.getTelefono().toLowerCase().contains(t)))
                .toList());
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdProveedor())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colContacto.setCellValueFactory(d ->
                new SimpleStringProperty(nvl(d.getValue().getContacto())));
        colTelefono.setCellValueFactory(d ->
                new SimpleStringProperty(nvl(d.getValue().getTelefono())));

        // Badge de estado activo/inactivo
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Proveedor p = (Proveedor) getTableRow().getItem();
                Label badge = new Label(p.getActivo() ? "Activo" : "Inactivo");
                badge.setStyle("-fx-background-color: "
                        + (p.getActivo() ? "#DCFCE7" : "#FEE2E2")
                        + "; -fx-text-fill: "
                        + (p.getActivo() ? "#15803D" : "#DC2626")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar     = crearBtn("Editar",      "#2563EB", "#EFF6FF");
            private final Button btnActivar    = crearBtn("Activar",     "#15803D", "#DCFCE7");
            private final Button btnDesactivar = crearBtn("Desactivar",  "#B45309", "#FEF3C7");
            private final Button btnEliminar   = crearBtn("Eliminar",    "#DC2626", "#FEE2E2");
            private final HBox box = new HBox(5, btnEditar, btnActivar, btnDesactivar, btnEliminar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Proveedor p = (Proveedor) getTableRow().getItem();

                btnEditar.setOnAction(e -> abrirFormulario(p));
                btnActivar.setOnAction(e -> activar(p));
                btnDesactivar.setOnAction(e -> desactivar(p));
                btnEliminar.setOnAction(e -> eliminar(p));

                // Mostrar Activar o Desactivar según estado
                btnActivar.setVisible(!p.getActivo());
                btnActivar.setManaged(!p.getActivo());
                btnDesactivar.setVisible(p.getActivo());
                btnDesactivar.setManaged(p.getActivo());

                setGraphic(box);
            }
        });
    }

    private Button crearBtn(String texto, String colorTexto, String colorFondo) {
        Button btn = new Button(texto);
        btn.setStyle("-fx-background-color: " + colorFondo + "; -fx-text-fill: "
                + colorTexto + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 7; -fx-cursor: hand;");
        return btn;
    }

    private void cargarProveedores() {
        paginador.setDatos(proveedorService.obtenerTodosIncluyendoInactivos());
    }

    @FXML public void onNuevoProveedor() { abrirFormulario(null); }

    @FXML
    public void onBuscar() {
        String texto = txtBuscar.getText().trim();
        tablaProveedores.setItems(
                FXCollections.observableArrayList(proveedorService.buscar(texto)));
    }

    @FXML
    public void onVerTodos() {
        txtBuscar.clear();
        lblMensaje.setText("");
    }

    private void activar(Proveedor p) {
        confirmar("¿Activar al proveedor " + p.getNombre() + "?", "Confirmar activación",
                () -> {
                    proveedorService.activar(p.getIdProveedor());
                    cargarProveedores();
                    mostrarMensaje("Proveedor activado correctamente.", false);
                });
    }

    private void desactivar(Proveedor p) {
        confirmar("¿Desactivar al proveedor " + p.getNombre() + "?", "Confirmar desactivación",
                () -> {
                    proveedorService.desactivar(p.getIdProveedor());
                    cargarProveedores();
                    mostrarMensaje("Proveedor desactivado.", false);
                });
    }

    private void eliminar(Proveedor p) {
        confirmar("¿Eliminar permanentemente al proveedor " + p.getNombre()
                        + "? Esta acción no se puede deshacer.", "Confirmar eliminación",
                () -> {
                    try {
                        proveedorService.eliminar(p.getIdProveedor());
                        cargarProveedores();
                        mostrarMensaje("Proveedor eliminado.", false);
                    } catch (Exception e) {
                        mostrarMensaje("No se puede eliminar: tiene compras asociadas.", true);
                    }
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

    private void abrirFormulario(Proveedor proveedor) {
        try {
            SpringFXMLLoader.LoadResult<ProveedorFormController> result =
                    fxmlLoader.loadWithController("/fxml/proveedor_form.fxml");
            result.controller.setProveedor(proveedor);
            result.controller.setOnGuardado(() -> {
                cargarProveedores();
                mostrarMensaje("Proveedor guardado correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(proveedor == null ? "Nuevo Proveedor" : "Editar Proveedor");
            stage.setScene(new Scene(result.root, 480, 460));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de proveedor", e);
            mostrarMensaje("Error al abrir el formulario.", true);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }

    private String nvl(String s) { return s != null ? s : "—"; }
}