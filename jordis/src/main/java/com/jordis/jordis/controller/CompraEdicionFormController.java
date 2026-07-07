package com.jordis.jordis.controller;

import com.jordis.jordis.model.Compra;
import com.jordis.jordis.model.CompraProducto;
import com.jordis.jordis.service.CompraService;
import com.jordis.jordis.service.AutenticacionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompraEdicionFormController {

    @FXML private Text txtTitulo;
    @FXML private Label lblResumenCompra;
    @FXML private TableView<FilaEdicion> tablaProductos;
    @FXML private TableColumn<FilaEdicion, String> colProducto;
    @FXML private TableColumn<FilaEdicion, String> colCantidadPedida;
    @FXML private TableColumn<FilaEdicion, String> colCantidadRecibida;
    @FXML private TableColumn<FilaEdicion, String> colCosto;
    @FXML private TextArea  txtMotivo;
    @FXML private CheckBox  chkNotaCredito;
    @FXML private Label     lblError;
    @FXML private Button    btnGuardar;

    private final CompraService        compraService;
    private final AutenticacionService autenticacionService;

    private Compra compra;
    private Runnable onGuardado;
    private final ObservableList<FilaEdicion> filas = FXCollections.observableArrayList();

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().detalle.getProducto().getNombre()));
        colCantidadPedida.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().detalle.getCantidad())));
        colCosto.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().detalle.getCostoUnitario().toPlainString()));

        // Columna de cantidad recibida — editable con TextField
        colCantidadRecibida.setCellFactory(col -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setStyle("-fx-font-size: 12; -fx-border-color: #BFDBFE; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6;");
                tf.setPrefWidth(80);
                tf.textProperty().addListener((obs, old, val) -> {
                    if (getIndex() >= 0 && getIndex() < filas.size()) {
                        filas.get(getIndex()).cantidadRecibida = val;
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                FilaEdicion fila = (FilaEdicion) getTableRow().getItem();
                if (tf.getText().isEmpty()) {
                    tf.setText(String.valueOf(fila.detalle.getCantidad()));
                }
                setGraphic(tf);
            }
        });

        tablaProductos.setItems(filas);
    }

    public void setCompra(Compra compra) {
        this.compra = compra;
        txtTitulo.setText("Editar Compra #" + compra.getIdCompra());
        lblResumenCompra.setText("Proveedor: " + compra.getProveedor().getNombre()
                + "  |  Fecha: " + compra.getFechaPedido().format(FMT)
                + "  |  Total: RD$" + compra.getTotalCompra().toPlainString());

        filas.clear();
        for (CompraProducto det : compra.getDetalles()) {
            filas.add(new FilaEdicion(det,
                    String.valueOf(det.getCantidad())));
        }
        txtMotivo.clear();
        chkNotaCredito.setSelected(false);
        lblError.setText("");
    }

    public void setOnGuardado(Runnable cb) { this.onGuardado = cb; }

    @FXML
    public void onGuardar() {
        lblError.setText("");

        if (txtMotivo.getText().trim().isEmpty()) {
            lblError.setText("El motivo del cambio es obligatorio."); return;
        }

        // Construir mapa de cantidades recibidas
        Map<Integer, Integer> cantidades = new HashMap<>();
        for (FilaEdicion fila : filas) {
            try {
                int cant = Integer.parseInt(
                        fila.cantidadRecibida == null || fila.cantidadRecibida.isBlank()
                                ? String.valueOf(fila.detalle.getCantidad())
                                : fila.cantidadRecibida.trim());
                if (cant < 0) {
                    lblError.setText("Las cantidades no pueden ser negativas."); return;
                }
                cantidades.put(fila.detalle.getProducto().getIdProducto(), cant);
            } catch (NumberFormatException e) {
                lblError.setText("Las cantidades deben ser números enteros."); return;
            }
        }

        try {
            compraService.editarCompra(
                    compra.getIdCompra(),
                    cantidades,
                    txtMotivo.getText().trim(),
                    chkNotaCredito.isSelected(),
                    autenticacionService.getUsuarioActivo().getIdUsuario());
            if (onGuardado != null) onGuardado.run();
            cerrar();
        } catch (Exception e) {
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML public void onCancelar() { cerrar(); }

    private void cerrar() {
        ((Stage) btnGuardar.getScene().getWindow()).close();
    }

    static class FilaEdicion {
        final CompraProducto detalle;
        String cantidadRecibida;

        FilaEdicion(CompraProducto detalle, String cantidadRecibida) {
            this.detalle = detalle;
            this.cantidadRecibida = cantidadRecibida;
        }
    }
}