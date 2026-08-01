package com.jordis.jordis.controller;

import com.jordis.jordis.model.TipoDevolucion;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.model.VentaProducto;
import com.jordis.jordis.service.DevolucionService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DevolucionFormController {

    private static final String OTRO_MOTIVO = "Otro motivo";

    private static final List<String> MOTIVOS_PREDEFINIDOS = List.of(
            "Producto defectuoso o dañado",
            "Producto incorrecto (no es el que el cliente pidió)",
            "Cliente cambió de opinión",
            "Talla, color o modelo no adecuado",
            "Producto incompleto o le faltan piezas/accesorios",
            "Producto no funciona correctamente",
            "Error del cajero al facturar",
            OTRO_MOTIVO
    );

    @FXML private Label lblVenta;
    @FXML private Label lblTipoResultante;
    @FXML private TableView<ItemDevolucionUI> tablaItems;
    @FXML private TableColumn<ItemDevolucionUI, String> colProducto;
    @FXML private TableColumn<ItemDevolucionUI, String> colVendida;
    @FXML private TableColumn<ItemDevolucionUI, String> colDevuelta;
    @FXML private TableColumn<ItemDevolucionUI, String> colDisponible;
    @FXML private TableColumn<ItemDevolucionUI, Integer> colCantidad;
    @FXML private ComboBox<String> cmbMotivo;
    @FXML private TextArea txtMotivoOtro;
    @FXML private TextArea txtObservaciones;
    @FXML private Label lblError;
    @FXML private Button btnConfirmar;

    private final DevolucionService devolucionService;

    private Venta venta;
    private Runnable onGuardado;
    private Stage stage;

    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().nombreProducto()));
        colVendida.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidadVendida())));
        colDevuelta.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidadYaDevuelta())));
        colDisponible.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().cantidadDisponible())));

        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ItemDevolucionUI fila = getTableRow().getItem();
                Spinner<Integer> spinner = new Spinner<>(0, fila.cantidadDisponible(), 0);
                spinner.setEditable(true);
                spinner.setPrefWidth(90);
                spinner.valueProperty().addListener((obs, old, val) ->
                        fila.cantidadSeleccionadaProperty().set(val));
                setGraphic(spinner);
            }
        });

        // Motivo: lista de razones predefinidas; solo si eligen "Otro motivo"
        // se habilita el cuadro de texto libre.
        cmbMotivo.setItems(FXCollections.observableArrayList(MOTIVOS_PREDEFINIDOS));
        txtMotivoOtro.setVisible(false);
        txtMotivoOtro.setManaged(false);
        cmbMotivo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            boolean esOtro = OTRO_MOTIVO.equals(val);
            txtMotivoOtro.setVisible(esOtro);
            txtMotivoOtro.setManaged(esOtro);
            if (!esOtro) txtMotivoOtro.clear();
        });
    }

    public void prepararParaVenta(Venta venta) {
        this.venta = venta;
        lblError.setText("");
        cmbMotivo.getSelectionModel().clearSelection();
        txtMotivoOtro.clear();
        txtMotivoOtro.setVisible(false);
        txtMotivoOtro.setManaged(false);
        txtObservaciones.clear();

        lblVenta.setText((venta.getNumeroFactura() != null
                ? "Factura " + venta.getNumeroFactura()
                : "Venta #" + venta.getIdVenta())
                + "  ·  " + (venta.getCliente() != null
                ? venta.getCliente().getNombreCompleto() : "Cliente ocasional"));

        // El tipo de devolución no lo elige el cajero — lo determina la ley
        // según si la venta tenía comprobante fiscal o no.
        TipoDevolucion tipo = devolucionService.determinarTipoDevolucion(venta);
        if (tipo == TipoDevolucion.NOTA_CREDITO) {
            lblTipoResultante.setText(
                    "Esta devolución generará una Nota de Crédito Fiscal (NCF B04), "
                            + "porque la venta original tiene comprobante fiscal.");
        } else {
            lblTipoResultante.setText(
                    "Esta devolución se registrará como saldo a favor interno "
                            + "(la venta original no tiene comprobante fiscal).");
        }

        var filas = venta.getDetalles().stream()
                .map(this::construirFila)
                .filter(f -> f.cantidadDisponible() > 0)
                .toList();
        tablaItems.setItems(FXCollections.observableArrayList(filas));
    }

    private ItemDevolucionUI construirFila(VentaProducto detalle) {
        int disponible = devolucionService.obtenerCantidadDisponibleParaDevolver(detalle);
        int yaDevuelto = detalle.getCantidad() - disponible;
        return new ItemDevolucionUI(
                detalle.getProducto().getIdProducto(),
                detalle.getProducto().getNombre(),
                detalle.getCantidad(),
                yaDevuelto,
                disponible);
    }

    public void setOnGuardado(Runnable onGuardado) {
        this.onGuardado = onGuardado;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void onConfirmar() {
        lblError.setText("");

        Map<Integer, Integer> items = new LinkedHashMap<>();
        for (ItemDevolucionUI fila : tablaItems.getItems()) {
            int cantidad = fila.cantidadSeleccionadaProperty().get();
            if (cantidad > 0) {
                items.put(fila.idProducto(), cantidad);
            }
        }

        if (items.isEmpty()) {
            lblError.setText("Selecciona al menos un producto y una cantidad a devolver.");
            return;
        }

        String motivoSeleccionado = cmbMotivo.getSelectionModel().getSelectedItem();
        if (motivoSeleccionado == null) {
            lblError.setText("Selecciona el motivo de la devolución.");
            return;
        }
        String motivo;
        if (OTRO_MOTIVO.equals(motivoSeleccionado)) {
            String detalleMotivo = txtMotivoOtro.getText() != null
                    ? txtMotivoOtro.getText().trim() : "";
            if (detalleMotivo.isEmpty()) {
                lblError.setText("Describe el motivo de la devolución.");
                return;
            }
            motivo = detalleMotivo;
        } else {
            motivo = motivoSeleccionado;
        }

        try {
            devolucionService.registrarDevolucion(
                    venta, items, motivo,
                    txtObservaciones.getText() != null ? txtObservaciones.getText().trim() : null);

            if (venta.estaAnulada()) {
                Alert aviso = new Alert(Alert.AlertType.INFORMATION);
                aviso.setTitle("Venta anulada automáticamente");
                aviso.setHeaderText("Se devolvió el 100% de los productos");
                aviso.setContentText("Como ya no queda nada por devolver de esta venta, "
                        + "el sistema la marcó automáticamente como ANULADA.");
                if (stage != null) aviso.initOwner(stage);
                aviso.showAndWait();
            }

            if (onGuardado != null) onGuardado.run();
            if (stage != null) stage.close();
        } catch (Exception e) {
            log.error("Error registrando devolución", e);
            lblError.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    public void onCancelar() {
        if (stage != null) stage.close();
    }

    /** Fila liviana solo para la tabla — no es una entidad JPA. */
    public static class ItemDevolucionUI {
        private final Integer idProducto;
        private final String nombreProducto;
        private final int cantidadVendida;
        private final int cantidadYaDevuelta;
        private final int cantidadDisponible;
        private final SimpleIntegerProperty cantidadSeleccionada = new SimpleIntegerProperty(0);

        public ItemDevolucionUI(Integer idProducto, String nombreProducto,
                                int cantidadVendida, int cantidadYaDevuelta,
                                int cantidadDisponible) {
            this.idProducto = idProducto;
            this.nombreProducto = nombreProducto;
            this.cantidadVendida = cantidadVendida;
            this.cantidadYaDevuelta = cantidadYaDevuelta;
            this.cantidadDisponible = cantidadDisponible;
        }

        public Integer idProducto() { return idProducto; }
        public String nombreProducto() { return nombreProducto; }
        public int cantidadVendida() { return cantidadVendida; }
        public int cantidadYaDevuelta() { return cantidadYaDevuelta; }
        public int cantidadDisponible() { return cantidadDisponible; }
        public SimpleIntegerProperty cantidadSeleccionadaProperty() { return cantidadSeleccionada; }
    }
}