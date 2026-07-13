package com.jordis.jordis.controller;

import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.repository.CategoriaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.util.Paginador;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoriaController {

    @FXML
    private TableView<Categoria> tablaCategorias;
    @FXML
    private TableColumn<Categoria, String> colId;
    @FXML
    private TableColumn<Categoria, String> colNombre;
    @FXML
    private TableColumn<Categoria, String> colDescripcion;
    @FXML
    private TableColumn<Categoria, String> colProductos;
    @FXML
    private TableColumn<Categoria, Void> colAcciones;
    @FXML
    private Label lblMensaje;
    @FXML private TextField txtBuscar;

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private Paginador<Categoria> paginador;

    @FXML
    public void initialize() {
        configurarColumnas();

        paginador = new Paginador<>(tablaCategorias);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaCategorias.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) ->
                filtrarCategorias(val));

        cargarCategorias();
    }


    private void filtrarCategorias(String texto) {
        List<Categoria> base = categoriaRepository.findAll();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base); return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(c -> c.getNombre().toLowerCase().contains(t)
                        || (c.getDescripcion() != null
                        && c.getDescripcion().toLowerCase().contains(t)))
                .toList());
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(
                        String.valueOf(d.getValue().getIdCategoria())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colDescripcion.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDescripcion() != null
                                ? d.getValue().getDescripcion() : "—"));

        // Contar productos de cada categoría
        colProductos.setCellValueFactory(d -> {
            long count = productoRepository.findByActivoTrue().stream()
                    .filter(p -> p.getCategoria() != null
                            && p.getCategoria().getIdCategoria()
                            .equals(d.getValue().getIdCategoria()))
                    .count();
            return new SimpleStringProperty(String.valueOf(count));
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar = crearBtn("Editar", "#2563EB", "#EFF6FF");
            private final Button btnEliminar = crearBtn("Eliminar", "#DC2626", "#FEF2F2");
            private final HBox box = new HBox(6, btnEditar, btnEliminar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Categoria c = (Categoria) getTableRow().getItem();
                btnEditar.setOnAction(e -> editarCategoria(c));
                btnEliminar.setOnAction(e -> eliminarCategoria(c));
                setGraphic(box);
            }
        });
    }

    @FXML
    public void onVerTodas() { txtBuscar.clear(); }

    private Button crearBtn(String texto, String colorTexto, String colorFondo) {
        Button btn = new Button(texto);
        btn.setStyle("-fx-background-color: " + colorFondo
                + "; -fx-text-fill: " + colorTexto
                + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 8; -fx-cursor: hand;");
        return btn;
    }

    private void cargarCategorias() {
        paginador.setDatos(categoriaRepository.findAll());
    }

    @FXML
    public void onNuevaCategoria() {
        mostrarDialogo(null);
    }

    private void editarCategoria(Categoria categoria) {
        mostrarDialogo(categoria);
    }

    private void mostrarDialogo(Categoria categoriaExistente) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(categoriaExistente == null
                ? "Nueva Categoría" : "Editar Categoría");
        stage.setResizable(false);

        // Campos
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Smartphones, Laptops, Accesorios...");
        txtNombre.setPrefHeight(36);
        txtNombre.setStyle("-fx-font-size: 13; -fx-border-color: #BFDBFE;"
                + " -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 10;");

        TextArea txtDescripcion = new TextArea();
        txtDescripcion.setPromptText(
                "Descripción de la categoría (opcional)...");
        txtDescripcion.setPrefHeight(90);
        txtDescripcion.setWrapText(true);
        txtDescripcion.setStyle("-fx-font-size: 13; -fx-border-color: #BFDBFE;"
                + " -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 10;");

        Label lblError = new Label("");
        lblError.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12;");
        lblError.setWrapText(true);

        if (categoriaExistente != null) {
            txtNombre.setText(categoriaExistente.getNombre());
            txtDescripcion.setText(
                    categoriaExistente.getDescripcion() != null
                            ? categoriaExistente.getDescripcion() : "");
        }

        // Botones
        Button btnGuardar = new Button(
                categoriaExistente == null ? "Crear categoría" : "Guardar cambios");
        btnGuardar.setPrefHeight(38);
        btnGuardar.setPrefWidth(200);
        btnGuardar.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white;"
                + " -fx-font-size: 13; -fx-background-radius: 6; -fx-cursor: hand;");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setPrefHeight(38);
        btnCancelar.setPrefWidth(120);
        btnCancelar.setStyle("-fx-background-color: transparent;"
                + " -fx-text-fill: #2563EB; -fx-border-color: #2563EB;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-font-size: 13; -fx-cursor: hand;");

        btnCancelar.setOnAction(e -> stage.close());
        btnGuardar.setOnAction(e -> {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) {
                lblError.setText("El nombre de la categoría es obligatorio.");
                return;
            }
            try {
                if (categoriaExistente == null) {
                    Categoria nueva = new Categoria();
                    nueva.setNombre(nombre);
                    nueva.setDescripcion(
                            txtDescripcion.getText().trim().isEmpty()
                                    ? null : txtDescripcion.getText().trim());
                    categoriaRepository.save(nueva);
                    mostrarMensaje("Categoría creada correctamente.", false);
                } else {
                    categoriaExistente.setNombre(nombre);
                    categoriaExistente.setDescripcion(
                            txtDescripcion.getText().trim().isEmpty()
                                    ? null : txtDescripcion.getText().trim());
                    categoriaRepository.save(categoriaExistente);
                    mostrarMensaje("Categoría actualizada correctamente.", false);
                }
                cargarCategorias();
                stage.close();
            } catch (Exception ex) {
                lblError.setText("Error: " + ex.getMessage());
            }
        });

        // Layout
        javafx.scene.text.Text titulo = new javafx.scene.text.Text(
                categoriaExistente == null
                        ? "Nueva Categoría" : "Editar Categoría");
        titulo.setStyle("-fx-font-size: 16; -fx-font-weight: bold;"
                + " -fx-fill: #1E40AF;");

        VBox vNombre = new VBox(6,
                new Label("Nombre *") {{
                    setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
                }},
                txtNombre);

        VBox vDesc = new VBox(6,
                new Label("Descripción") {{
                    setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
                }},
                txtDescripcion);

        HBox botones = new HBox(10, btnGuardar, btnCancelar);
        botones.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox root = new VBox(16, titulo, vNombre, vDesc, lblError, botones);
        root.setStyle("-fx-background-color: white; -fx-padding: 28;");
        root.setPrefWidth(460);

        stage.setScene(new javafx.scene.Scene(root));
        stage.showAndWait();
    }


    private void eliminarCategoria(Categoria categoria) {
        long productosAsociados = productoRepository.findByActivoTrue().stream()
                .filter(p -> p.getCategoria() != null
                        && p.getCategoria().getIdCategoria()
                        .equals(categoria.getIdCategoria()))
                .count();

        if (productosAsociados > 0) {
            mostrarMensaje("No se puede eliminar: tiene "
                    + productosAsociados + " producto(s) asociado(s).", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar la categoría '" + categoria.getNombre() + "'?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                categoriaRepository.delete(categoria);
                cargarCategorias();
                mostrarMensaje("Categoría eliminada.", false);
            }
        });
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}