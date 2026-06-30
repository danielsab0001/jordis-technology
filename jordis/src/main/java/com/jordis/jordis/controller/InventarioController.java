package com.jordis.jordis.controller;

import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.InventarioService;
import com.jordis.jordis.service.ProductoService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    @FXML private TableView<Producto> tablaInventario;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colMarca;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colStock;
    @FXML private TableColumn<Producto, String> colMinimo;
    @FXML private TableColumn<Producto, String> colEstado;
    @FXML private TableColumn<Producto, String> colRecomendado;
    @FXML private TableColumn<Producto, String> colPrecioVenta;
    @FXML private TableColumn<Producto, String> colPrecioComp;
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private TextField txtPrecioMin;
    @FXML private TextField txtPrecioMax;
    @FXML private TableColumn<Producto, String> colPrecioSug;
    @FXML private Label lblMensaje;
    @FXML private TextField txtMargen;
    @FXML private TableColumn<Producto, String> colMargen;

    private final InventarioService inventarioService;
    private final ProductoService productoService;
    private final AutenticacionService autenticacionService;

    @FXML
    public void initialize() {
        // Cargar categorías en el combo
        cmbCategoria.getItems().add(null); // opción "Todas"
        cmbCategoria.getItems().addAll(productoService.obtenerCategorias());
        cmbCategoria.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Categoria c) {
                return c == null ? "Todas las categorías" : c.getNombre();
            }
            @Override public Categoria fromString(String s) { return null; }
        });
        cmbCategoria.setValue(null);

        configurarColumnas();
        cargarInventario();
    }

    @FXML
    public void onAplicarMargen() {
        try {
            BigDecimal margen = new BigDecimal(txtMargen.getText().trim());
            if (margen.compareTo(BigDecimal.ZERO) <= 0) {
                mostrarMensaje("El margen debe ser mayor a 0.", true); return;
            }
            // Factor: 30% → 1.30
            BigDecimal factor = BigDecimal.ONE.add(
                    margen.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            inventarioService.actualizarPreciosSugeridos(factor);
            cargarInventario();
            mostrarMensaje("Precios sugeridos actualizados con margen del "
                    + margen.toPlainString() + "%.", false);
        } catch (NumberFormatException e) {
            mostrarMensaje("El margen debe ser un número válido.", true);
        }
    }

    private void configurarColumnas() {
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colMarca.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getMarca() != null ? d.getValue().getMarca() : "—"));
        colCategoria.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCategoria() != null
                                ? d.getValue().getCategoria().getNombre() : "—"));
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); setText(null); setStyle(""); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                int stock  = p.getStock();
                int minimo = p.getStockMinimo();

                String colorFondo, colorTexto;
                if (stock == 0) {
                    colorFondo = "#FEE2E2"; colorTexto = "#DC2626"; // rojo fuerte
                } else if (stock <= minimo) {
                    colorFondo = "#FFEDD5"; colorTexto = "#C2410C"; // naranja
                } else if (stock <= minimo * 2) {
                    colorFondo = "#FEF3C7"; colorTexto = "#B45309"; // amarillo
                } else {
                    colorFondo = "#DCFCE7"; colorTexto = "#15803D"; // verde
                }

                setText(String.valueOf(stock));
                setStyle("-fx-background-color: " + colorFondo
                        + "; -fx-text-fill: " + colorTexto
                        + "; -fx-font-weight: bold; -fx-alignment: CENTER;");
                setGraphic(null);
            }
        });
        colMinimo.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getStockMinimo())));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                boolean bajo = p.isStockBajo();
                Label badge = new Label(bajo ? "Stock bajo" : "OK");
                badge.setStyle("-fx-background-color: " + (bajo ? "#FEF3C7" : "#DCFCE7")
                        + "; -fx-text-fill: " + (bajo ? "#B45309" : "#15803D")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colRecomendado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                int rec = inventarioService.calcularRecomendacionCompra(p);
                Label lbl = new Label(rec > 0 ? "Comprar " + rec + " u." : "Suficiente");
                lbl.setStyle("-fx-text-fill: " + (rec > 0 ? "#B45309" : "#64748B")
                        + "; -fx-font-size: 11;"
                        + (rec > 0 ? " -fx-font-weight: bold;" : ""));
                setGraphic(lbl);
            }
        });

        colPrecioVenta.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().getPrecioUnitario().toPlainString()));
        colPrecioComp.setCellValueFactory(d -> {
            var precio = d.getValue().getUltimoPrecioCompra();
            return new SimpleStringProperty(
                    precio != null ? "RD$" + precio.toPlainString() : "—");
        });
        colPrecioSug.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); setText(null); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                if (p.getPrecioSugerido() == null) {
                    setText("—"); setGraphic(null); return;
                }

                BigDecimal actual    = p.getPrecioUnitario();
                BigDecimal sugerido  = p.getPrecioSugerido();
                BigDecimal diferencia = actual.subtract(sugerido).abs()
                        .divide(sugerido, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                String colorFondo, colorTexto;
                if (diferencia.compareTo(new BigDecimal("40")) >= 0) {
                    colorFondo = "#FEE2E2"; colorTexto = "#DC2626"; // rojo fuerte
                } else if (diferencia.compareTo(new BigDecimal("20")) >= 0) {
                    colorFondo = "#FFEDD5"; colorTexto = "#C2410C"; // naranja
                } else if (diferencia.compareTo(new BigDecimal("10")) >= 0) {
                    colorFondo = "#FEF3C7"; colorTexto = "#B45309"; // amarillo
                } else {
                    colorFondo = "#DCFCE7"; colorTexto = "#15803D"; // verde — bien alineado
                }

                VBox contenido = new VBox(1);
                Label lblPrecio = new Label("RD$" + sugerido.toPlainString());
                lblPrecio.setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorTexto
                        + "; -fx-font-size: 12;");
                Label lblDiff = new Label("(" + (actual.compareTo(sugerido) > 0 ? "+" : "-")
                        + diferencia.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%)");
                lblDiff.setStyle("-fx-font-size: 10; -fx-text-fill: " + colorTexto + ";");

                contenido.getChildren().addAll(lblPrecio, lblDiff);
                contenido.setStyle("-fx-background-color: " + colorFondo
                        + "; -fx-padding: 2 6; -fx-background-radius: 4;");
                setGraphic(contenido);
                setText(null);
            }
        });

        colMargen.setCellFactory(col -> new TableCell<>() {
            private final TextField tf = new TextField();
            private final Button btn  = new Button("✓");
            private final HBox box    = new HBox(4, tf, btn);

            {
                tf.setPrefWidth(45);
                tf.setStyle("-fx-font-size: 11; -fx-border-color: #BFDBFE; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 4;");
                btn.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                        + " -fx-border-color: #BBF7D0; -fx-border-radius: 4; "
                        + "-fx-background-radius: 4; -fx-font-size: 11; -fx-padding: 2 6;");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Producto p = (Producto) getTableRow().getItem();
                tf.setText("30"); // valor por defecto

                btn.setOnAction(e -> {
                    try {
                        BigDecimal margen = new BigDecimal(tf.getText().trim());
                        if (margen.compareTo(BigDecimal.ZERO) <= 0) {
                            mostrarMensaje("El margen debe ser mayor a 0.", true);
                            return;
                        }
                        BigDecimal factor = BigDecimal.ONE.add(
                                margen.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

                        // Actualizar en BD
                        inventarioService.actualizarPrecioSugeridoProducto(p.getIdProducto(), factor);

                        // Actualizar solo el item de esta fila sin recargar toda la tabla
                        Producto actualizado = inventarioService.obtenerProductoPorId(p.getIdProducto());
                        int indice = tablaInventario.getItems().indexOf(p);
                        if (indice >= 0) {
                            tablaInventario.getItems().set(indice, actualizado);
                        }

                        mostrarMensaje("Precio sugerido de '" + p.getNombre()
                                + "' actualizado con margen " + margen + "%.", false);
                    } catch (NumberFormatException ex) {
                        mostrarMensaje("El margen debe ser un número.", true);
                    }
                });
                setGraphic(box);
            }
        });
    }

    private void cargarInventario() {
        tablaInventario.setItems(
                FXCollections.observableArrayList(inventarioService.obtenerInventario()));
    }

    @FXML
    public void onFiltrar() {
        String texto        = txtBuscar.getText().trim().toLowerCase();
        Categoria catSel    = cmbCategoria.getValue();
        String minStr       = txtPrecioMin.getText().trim();
        String maxStr       = txtPrecioMax.getText().trim();

        BigDecimal precioMin = null;
        BigDecimal precioMax = null;

        try {
            if (!minStr.isEmpty()) precioMin = new BigDecimal(minStr);
            if (!maxStr.isEmpty()) precioMax = new BigDecimal(maxStr);
        } catch (NumberFormatException e) {
            mostrarMensaje("El rango de precios debe ser numérico.", true);
            return;
        }

        final BigDecimal pMin = precioMin;
        final BigDecimal pMax = precioMax;

        List<Producto> resultado = inventarioService.obtenerInventario()
                .stream()
                .filter(p -> {
                    boolean matchTexto = texto.isEmpty()
                            || p.getNombre().toLowerCase().contains(texto)
                            || (p.getMarca() != null &&
                            p.getMarca().toLowerCase().contains(texto));

                    boolean matchCat = catSel == null
                            || (p.getCategoria() != null &&
                            p.getCategoria().getIdCategoria()
                                    .equals(catSel.getIdCategoria()));

                    boolean matchMin = pMin == null
                            || p.getPrecioUnitario().compareTo(pMin) >= 0;

                    boolean matchMax = pMax == null
                            || p.getPrecioUnitario().compareTo(pMax) <= 0;

                    return matchTexto && matchCat && matchMin && matchMax;
                })
                .toList();

        tablaInventario.setItems(FXCollections.observableArrayList(resultado));
        mostrarMensaje(resultado.isEmpty()
                ? "No se encontraron productos con esos filtros." : "", resultado.isEmpty());
    }

    @FXML public void onVerStockBajo() {
        List<Producto> bajos = inventarioService.obtenerStockBajo();
        tablaInventario.setItems(FXCollections.observableArrayList(bajos));
        mostrarMensaje(bajos.size() + " producto(s) con stock bajo.", false);
    }

    @FXML public void onVerTodo() {
        cargarInventario();
        txtBuscar.clear();
        cmbCategoria.setValue(null);
        txtPrecioMin.clear();
        txtPrecioMax.clear();
        lblMensaje.setText("");
    }

    @FXML public void onAjusteManual() {
        Producto seleccionado = tablaInventario.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarMensaje("Selecciona un producto de la tabla primero.", true);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ajuste de inventario");
        dialog.setHeaderText("Producto: " + seleccionado.getNombre()
                + " | Stock actual: " + seleccionado.getStock());

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Ej: 5 para sumar, -3 para restar");
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo del ajuste");

        VBox content = new VBox(10,
                new Label("Cantidad (positivo = entrada, negativo = salida):"),
                txtCantidad,
                new Label("Motivo:"),
                txtMotivo);
        content.setStyle("-fx-padding: 16;");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    int cantidad = Integer.parseInt(txtCantidad.getText().trim());
                    inventarioService.ajustarStock(
                            seleccionado.getIdProducto(),
                            autenticacionService.getUsuarioActivo().getIdUsuario(),
                            cantidad,
                            txtMotivo.getText().trim());
                    cargarInventario();
                    mostrarMensaje("Ajuste realizado correctamente.", false);
                } catch (NumberFormatException e) {
                    mostrarMensaje("La cantidad debe ser un número entero.", true);
                } catch (Exception e) {
                    mostrarMensaje("Error: " + e.getMessage(), true);
                }
            }
        });
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}