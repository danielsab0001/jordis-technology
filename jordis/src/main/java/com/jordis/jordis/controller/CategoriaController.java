package com.jordis.jordis.controller;

import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.repository.CategoriaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoriaController {

    @FXML private TableView<Categoria> tablaCategorias;
    @FXML private TableColumn<Categoria, String> colId;
    @FXML private TableColumn<Categoria, String> colNombre;
    @FXML private TableColumn<Categoria, String> colDescripcion;
    @FXML private TableColumn<Categoria, String> colProductos;
    @FXML private TableColumn<Categoria, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository   productoRepository;

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarCategorias();
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
            private final Button btnEditar   = crearBtn("Editar",   "#2563EB", "#EFF6FF");
            private final Button btnEliminar = crearBtn("Eliminar", "#DC2626", "#FEF2F2");
            private final HBox box = new HBox(6, btnEditar, btnEliminar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Categoria c = (Categoria) getTableRow().getItem();
                btnEditar.setOnAction(e -> editarCategoria(c));
                btnEliminar.setOnAction(e -> eliminarCategoria(c));
                setGraphic(box);
            }
        });
    }

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
        tablaCategorias.setItems(
                FXCollections.observableArrayList(categoriaRepository.findAll()));
    }

    @FXML
    public void onNuevaCategoria() {
        mostrarDialogo(null);
    }

    private void editarCategoria(Categoria categoria) {
        mostrarDialogo(categoria);
    }

    private void mostrarDialogo(Categoria categoriaExistente) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(categoriaExistente == null
                ? "Nueva Categoría" : "Editar Categoría");
        dialog.setHeaderText(null);

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre de la categoría");
        txtNombre.setStyle("-fx-font-size: 13; -fx-border-color: #BFDBFE;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-padding: 4 10;");

        TextField txtDescripcion = new TextField();
        txtDescripcion.setPromptText("Descripción (opcional)");
        txtDescripcion.setStyle("-fx-font-size: 13; -fx-border-color: #BFDBFE;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-padding: 4 10;");

        Label lblError = new Label("");
        lblError.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12;");

        if (categoriaExistente != null) {
            txtNombre.setText(categoriaExistente.getNombre());
            txtDescripcion.setText(
                    categoriaExistente.getDescripcion() != null
                            ? categoriaExistente.getDescripcion() : "");
        }

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10,
                new Label("Nombre *"), txtNombre,
                new Label("Descripción"), txtDescripcion,
                lblError);
        content.setStyle("-fx-padding: 16;");

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes()
                .addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                String nombre = txtNombre.getText().trim();
                if (nombre.isEmpty()) {
                    mostrarMensaje("El nombre de la categoría es obligatorio.", true);
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
                } catch (Exception e) {
                    mostrarMensaje("Error: " + e.getMessage(), true);
                }
            }
        });
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