package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.InventarioService;
import com.jordis.jordis.service.ProductoService;
import com.jordis.jordis.util.Paginador;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventarioController {

    @FXML private TableView<Producto>              tablaInventario;
    @FXML private TableColumn<Producto, String>    colNombre;
    @FXML private TableColumn<Producto, String>    colMarca;
    @FXML private TableColumn<Producto, String>    colCategoria;
    @FXML private TableColumn<Producto, String>    colStock;
    @FXML private TableColumn<Producto, String>    colMinimo;
    @FXML private TableColumn<Producto, String>    colEstado;
    @FXML private TableColumn<Producto, String>    colRecomendado;
    @FXML private TableColumn<Producto, String>    colPrecioVenta;
    @FXML private TableColumn<Producto, String>    colPrecioComp;
    @FXML private TableColumn<Producto, String>    colPrecioSug;
    @FXML private TableColumn<Producto, Void>      colAcciones;
    @FXML private TextField                        txtBuscar;
    @FXML private ComboBox<Categoria>              cmbCategoria;
    @FXML private TextField                        txtPrecioMin;
    @FXML private TextField                        txtPrecioMax;
    @FXML private Label                            lblMensaje;

    private final InventarioService    inventarioService;
    private final ProductoService      productoService;
    private final AutenticacionService autenticacionService;
    private final SpringFXMLLoader     fxmlLoader;

    private Paginador<Producto> paginador;

    @FXML
    public void initialize() {
        // Categorías
        cmbCategoria.getItems().add(null);
        cmbCategoria.getItems().addAll(productoService.obtenerCategorias());
        cmbCategoria.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Categoria c) {
                return c == null ? "Todas las categorías" : c.getNombre();
            }
            @Override public Categoria fromString(String s) { return null; }
        });

        configurarColumnas();

        // Paginador
        paginador = new Paginador<>(tablaInventario);
        javafx.application.Platform.runLater(() -> {
            VBox padre = (VBox) tablaInventario.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        // Listeners de filtrado automático
        txtBuscar.textProperty().addListener((obs, old, val) -> aplicarFiltros());
        cmbCategoria.setOnAction(e -> aplicarFiltros());
        txtPrecioMin.textProperty().addListener((obs, old, val) -> aplicarFiltros());
        txtPrecioMax.textProperty().addListener((obs, old, val) -> aplicarFiltros());

        cargarInventario();
    }

    private void configurarColumnas() {
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colMarca.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getMarca() != null
                                ? d.getValue().getMarca() : "—"));
        colCategoria.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCategoria() != null
                                ? d.getValue().getCategoria().getNombre() : "—"));

        // Stock con color de fondo
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setStyle("");
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) return;
                Producto p = (Producto) getTableRow().getItem();
                int stock  = p.getStock();
                int minimo = p.getStockMinimo();
                String colorFondo, colorTexto;
                if (stock == 0) {
                    colorFondo = "#FEE2E2"; colorTexto = "#DC2626";
                } else if (stock <= minimo) {
                    colorFondo = "#FFEDD5"; colorTexto = "#C2410C";
                } else if (stock <= minimo * 2) {
                    colorFondo = "#FEF3C7"; colorTexto = "#B45309";
                } else {
                    colorFondo = "#DCFCE7"; colorTexto = "#15803D";
                }
                setText(String.valueOf(stock));
                setStyle("-fx-background-color: " + colorFondo
                        + "; -fx-text-fill: " + colorTexto
                        + "; -fx-font-weight: bold; -fx-alignment: CENTER;");
            }
        });

        colMinimo.setCellValueFactory(d ->
                new SimpleStringProperty(
                        String.valueOf(d.getValue().getStockMinimo())));

        // Estado con badge
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null); setStyle("");
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) return;
                Producto p   = (Producto) getTableRow().getItem();
                boolean bajo = p.isStockBajo();
                Label badge  = new Label(bajo ? "Stock bajo" : "OK");
                badge.setStyle("-fx-background-color: "
                        + (bajo ? "#FEF3C7" : "#DCFCE7")
                        + "; -fx-text-fill: "
                        + (bajo ? "#B45309" : "#15803D")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        // Recomendación
        colRecomendado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null); setStyle("");
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) return;
                Producto p = (Producto) getTableRow().getItem();
                int rec    = inventarioService.calcularRecomendacionCompra(p);
                Label lbl  = new Label(
                        rec > 0 ? "Comprar " + rec + " u." : "Suficiente");
                lbl.setStyle("-fx-text-fill: "
                        + (rec > 0 ? "#B45309" : "#64748B")
                        + "; -fx-font-size: 11;"
                        + (rec > 0 ? " -fx-font-weight: bold;" : ""));
                setGraphic(lbl);
            }
        });

        colPrecioVenta.setCellValueFactory(d ->
                new SimpleStringProperty(
                        "RD$" + d.getValue().getPrecioUnitario().toPlainString()));

        colPrecioComp.setCellValueFactory(d -> {
            var precio = d.getValue().getUltimoPrecioCompra();
            return new SimpleStringProperty(
                    precio != null ? "RD$" + precio.toPlainString() : "—");
        });

        // Precio sugerido con diferencia visual
        colPrecioSug.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); setGraphic(null); setStyle("");
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) return;
                Producto p = (Producto) getTableRow().getItem();
                if (p.getPrecioSugerido() == null) {
                    setText("—"); return;
                }
                BigDecimal actual   = p.getPrecioUnitario();
                BigDecimal sugerido = p.getPrecioSugerido();
                BigDecimal dif = actual.subtract(sugerido).abs()
                        .divide(sugerido, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                String colorFondo, colorTexto;
                if (dif.compareTo(new BigDecimal("40")) >= 0) {
                    colorFondo = "#FEE2E2"; colorTexto = "#DC2626";
                } else if (dif.compareTo(new BigDecimal("20")) >= 0) {
                    colorFondo = "#FFEDD5"; colorTexto = "#C2410C";
                } else if (dif.compareTo(new BigDecimal("10")) >= 0) {
                    colorFondo = "#FEF3C7"; colorTexto = "#B45309";
                } else {
                    colorFondo = "#DCFCE7"; colorTexto = "#15803D";
                }
                VBox contenido = new VBox(1);
                Label lblPrecio = new Label(
                        "RD$" + sugerido.toPlainString());
                lblPrecio.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                        + colorTexto + "; -fx-font-size: 12;");
                Label lblDiff = new Label(
                        "(" + (actual.compareTo(sugerido) > 0 ? "+" : "-")
                                + dif.setScale(1, RoundingMode.HALF_UP) + "%)");
                lblDiff.setStyle("-fx-font-size: 10; -fx-text-fill: "
                        + colorTexto + ";");
                contenido.getChildren().addAll(lblPrecio, lblDiff);
                contenido.setStyle("-fx-background-color: " + colorFondo
                        + "; -fx-padding: 2 6; -fx-background-radius: 4;");
                setGraphic(contenido);
            }
        });

        // Acciones por fila: margen individual + ajuste + historial
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final TextField tfMargen = new TextField("30");
            private final Button btnMargen   = new Button("% Aplicar");
            private final Button btnAjuste   = new Button("Ajustar Cantidad");
            private final Button btnHistorial= new Button("Historial Ajustes");
            private final HBox box = new HBox(4,
                    tfMargen, btnMargen, btnAjuste, btnHistorial);

            {
                tfMargen.setPrefWidth(42);
                tfMargen.setPrefHeight(28);
                tfMargen.setStyle("-fx-font-size: 11; -fx-border-color: #BFDBFE;"
                        + " -fx-border-radius: 4; -fx-background-radius: 4;"
                        + " -fx-padding: 2 4;");

                btnMargen.setPrefHeight(28);
                btnMargen.setStyle(
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                                + " -fx-border-color: #BBF7D0; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10;"
                                + " -fx-padding: 2 5; -fx-cursor: hand;");

                btnAjuste.setPrefHeight(28);
                btnAjuste.setStyle(
                        "-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                                + " -fx-border-color: #BFDBFE; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10;"
                                + " -fx-padding: 2 5; -fx-cursor: hand;");

                btnHistorial.setPrefHeight(28);
                btnHistorial.setStyle(
                        "-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9;"
                                + " -fx-border-color: #C4B5FD; -fx-border-radius: 4;"
                                + " -fx-background-radius: 4; -fx-font-size: 10;"
                                + " -fx-padding: 2 5; -fx-cursor: hand;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) return;

                Producto p = (Producto) getTableRow().getItem();

                btnMargen.setOnAction(e -> aplicarMargenProducto(p, tfMargen));
                btnAjuste.setOnAction(e -> abrirAjuste(p));
                btnHistorial.setOnAction(e -> abrirHistorial(p));

                setGraphic(box);
            }
        });
    }

    // ---- Acciones por fila ----

    private void aplicarMargenProducto(Producto producto, TextField tfMargen) {
        try {
            BigDecimal margen = new BigDecimal(tfMargen.getText().trim());
            if (margen.compareTo(BigDecimal.ZERO) <= 0) {
                mostrarMensaje("El margen debe ser mayor a 0.", true);
                return;
            }
            BigDecimal factor = BigDecimal.ONE.add(
                    margen.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            inventarioService.actualizarPrecioSugeridoProducto(
                    producto.getIdProducto(), factor);

            // Actualizar solo la fila sin recargar toda la tabla
            Producto actualizado =
                    inventarioService.obtenerProductoPorId(producto.getIdProducto());
            int indice = tablaInventario.getItems().indexOf(producto);
            if (indice >= 0) {
                tablaInventario.getItems().set(indice, actualizado);
            }
            mostrarMensaje("Precio sugerido de '"
                    + producto.getNombre() + "' actualizado con "
                    + margen.toPlainString() + "% de margen.", false);
        } catch (NumberFormatException e) {
            mostrarMensaje("El margen debe ser un número válido.", true);
        }
    }

    private void abrirAjuste(Producto producto) {
        try {
            SpringFXMLLoader.LoadResult<AjusteInventarioController> result =
                    fxmlLoader.loadWithController(
                            "/fxml/ajuste_inventario_form.fxml");
            result.controller.setProducto(producto);
            result.controller.setOnGuardado(() -> {
                Producto actualizado =
                        inventarioService.obtenerProductoPorId(
                                producto.getIdProducto());
                int indice = tablaInventario.getItems().indexOf(producto);
                if (indice >= 0) {
                    tablaInventario.getItems().set(indice, actualizado);
                }
                mostrarMensaje("Ajuste realizado correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Ajuste — " + producto.getNombre());
            stage.setScene(new Scene(result.root, 500, 480));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo ajuste de inventario", e);
            mostrarMensaje("Error: " + e.getMessage(), true);
        }
    }

    private void abrirHistorial(Producto producto) {
        try {
            SpringFXMLLoader.LoadResult<AjusteHistorialController> result =
                    fxmlLoader.loadWithController("/fxml/ajuste_historial.fxml");
            result.controller.setProducto(producto);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Ajustes — " + producto.getNombre());
            stage.setScene(new Scene(result.root, 780, 480));
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo historial de ajustes", e);
            mostrarMensaje("Error: " + e.getMessage(), true);
        }
    }

    // ---- Carga y filtrado ----

    private void cargarInventario() {
        paginador.setDatos(inventarioService.obtenerInventario());
    }

    private void aplicarFiltros() {
        String texto  = txtBuscar.getText().trim().toLowerCase();
        Categoria cat = cmbCategoria.getValue();
        String minStr = txtPrecioMin.getText().trim();
        String maxStr = txtPrecioMax.getText().trim();

        BigDecimal pMin = null, pMax = null;
        try { if (!minStr.isEmpty()) pMin = new BigDecimal(minStr); }
        catch (Exception ignored) {}
        try { if (!maxStr.isEmpty()) pMax = new BigDecimal(maxStr); }
        catch (Exception ignored) {}

        final BigDecimal fMin = pMin, fMax = pMax;

        List<Producto> resultado = inventarioService.obtenerInventario()
                .stream()
                .filter(p -> {
                    boolean matchTexto = texto.isEmpty()
                            || p.getNombre().toLowerCase().contains(texto)
                            || (p.getMarca() != null
                            && p.getMarca().toLowerCase().contains(texto));
                    boolean matchCat = cat == null
                            || (p.getCategoria() != null
                            && p.getCategoria().getIdCategoria()
                            .equals(cat.getIdCategoria()));
                    boolean matchMin = fMin == null
                            || p.getPrecioUnitario().compareTo(fMin) >= 0;
                    boolean matchMax = fMax == null
                            || p.getPrecioUnitario().compareTo(fMax) <= 0;
                    return matchTexto && matchCat && matchMin && matchMax;
                })
                .toList();

        paginador.setDatos(resultado);
        lblMensaje.setText("");
    }

    // Filtra la tabla para mostrar únicamente el producto indicado.
    // Se usa al navegar aquí desde el Centro de Alertas.
    public void filtrarPorProductoId(Integer idProducto) {
        if (idProducto == null) return;
        Producto p;
        try {
            p = inventarioService.obtenerProductoPorId(idProducto);
        } catch (Exception e) {
            mostrarMensaje("El producto de la alerta ya no existe.", true);
            return;
        }
        txtBuscar.setText(p.getNombre());
        cmbCategoria.setValue(null);
        txtPrecioMin.clear();
        txtPrecioMax.clear();
        paginador.setDatos(List.of(p));
        mostrarMensaje("Mostrando: " + p.getNombre(), false);
    }

    @FXML
    public void onVerStockBajo() {
        paginador.setDatos(inventarioService.obtenerStockBajo());
        mostrarMensaje(inventarioService.obtenerStockBajo().size()
                + " producto(s) con stock bajo.", false);
    }

    @FXML
    public void onVerTodo() {
        txtBuscar.clear();
        cmbCategoria.setValue(null);
        txtPrecioMin.clear();
        txtPrecioMax.clear();
        cargarInventario();
        lblMensaje.setText("");
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12;"
                + " -fx-text-fill: " + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}