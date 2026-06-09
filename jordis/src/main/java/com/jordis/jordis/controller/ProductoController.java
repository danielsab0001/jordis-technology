package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.ProductoService;
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
public class ProductoController {

    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto, String> colId;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colMarca;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colPrecio;
    @FXML private TableColumn<Producto, String> colStock;
    @FXML private TableColumn<Producto, String> colEstado;
    @FXML private TableColumn<Producto, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private Label lblMensaje;

    private final ProductoService productoService;
    private final SpringFXMLLoader fxmlLoader;

    @FXML
    public void initialize() {
        cargarCategorias();
        configurarColumnas();
        cargarProductos();
    }

    private void cargarCategorias() {
        List<Categoria> cats = productoService.obtenerCategorias();
        cmbCategoria.getItems().clear();
        cmbCategoria.getItems().addAll(cats);
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdProducto())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colMarca.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getMarca() != null ? d.getValue().getMarca() : "—"));
        colCategoria.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCategoria() != null
                                ? d.getValue().getCategoria().getNombre() : "—"));
        colPrecio.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().getPrecioUnitario().toPlainString()));
        colStock.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getStock())));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                Label badge = new Label(p.isStockBajo() ? "Stock bajo" : "Activo");
                badge.setStyle("-fx-background-color: "
                        + (p.isStockBajo() ? "#FEF3C7" : "#DCFCE7")
                        + "; -fx-text-fill: "
                        + (p.isStockBajo() ? "#B45309" : "#15803D")
                        + "; -fx-padding: 2 7; -fx-background-radius: 4;"
                        + " -fx-font-size: 10; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar   = crearBtn("Editar",   "#2563EB", "#EFF6FF");
            private final Button btnEliminar = crearBtn("Eliminar", "#DC2626", "#FEF2F2");
            private final HBox box = new HBox(6, btnEditar, btnEliminar);

            {
                btnEditar.setOnAction(e ->
                        abrirFormulario(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(e ->
                        eliminar(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private Button crearBtn(String texto, String colorTexto, String colorFondo) {
        Button btn = new Button(texto);
        btn.setStyle("-fx-background-color: " + colorFondo + "; -fx-text-fill: "
                + colorTexto + "; -fx-border-color: " + colorTexto
                + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-font-size: 10; -fx-padding: 3 8; -fx-cursor: hand;");
        return btn;
    }

    private void cargarProductos() {
        tablaProductos.setItems(
                FXCollections.observableArrayList(productoService.obtenerTodos()));
    }

    @FXML
    public void onNuevoProducto() {
        abrirFormulario(null);
    }

    @FXML
    public void onBuscar() {
        String texto     = txtBuscar.getText().trim();
        Categoria catSel = cmbCategoria.getValue();
        List<Producto> resultado;

        if (catSel != null) {
            resultado = productoService.obtenerPorCategoria(catSel.getIdCategoria());
        } else {
            resultado = productoService.buscar(texto);
        }
        tablaProductos.setItems(FXCollections.observableArrayList(resultado));
        lblMensaje.setText(resultado.isEmpty() ? "No se encontraron productos." : "");
    }

    @FXML
    public void onVerTodos() {
        cargarProductos();
        txtBuscar.clear();
        cmbCategoria.setValue(null);
        lblMensaje.setText("");
    }

    @FXML
    public void onVerStockBajo() {
        List<Producto> bajos = productoService.obtenerStockBajo();
        tablaProductos.setItems(FXCollections.observableArrayList(bajos));
        mostrarMensaje(bajos.size() + " producto(s) con stock bajo.", bajos.isEmpty());
    }

    private void eliminar(Producto producto) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar el producto '" + producto.getNombre()
                        + "'? Esta acción no se puede deshacer.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                productoService.eliminar(producto.getIdProducto());
                cargarProductos();
                mostrarMensaje("Producto eliminado correctamente.", false);
            }
        });
    }

    private void abrirFormulario(Producto producto) {
        try {
            SpringFXMLLoader.LoadResult<ProductoFormController> result =
                    fxmlLoader.loadWithController("/fxml/producto_form.fxml");

            result.controller.setProducto(producto);
            result.controller.setOnGuardado(() -> {
                cargarProductos();
                mostrarMensaje("Producto guardado correctamente.", false);
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(producto == null ? "Nuevo Producto" : "Editar Producto");
            stage.setScene(new Scene(result.root, 500, 480));
            stage.showAndWait();

        } catch (Exception e) {
            log.error("Error abriendo formulario de producto", e);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}