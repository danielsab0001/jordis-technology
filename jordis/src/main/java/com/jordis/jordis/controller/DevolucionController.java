package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Devolucion;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.DevolucionService;
import com.jordis.jordis.service.VentaService;
import com.jordis.jordis.util.Paginador;
import com.jordis.jordis.util.VentanaUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DevolucionController {

    @FXML private TableView<Devolucion> tablaDevoluciones;
    @FXML private TableColumn<Devolucion, String> colId;
    @FXML private TableColumn<Devolucion, String> colFactura;
    @FXML private TableColumn<Devolucion, String> colFecha;
    @FXML private TableColumn<Devolucion, String> colProductos;
    @FXML private TableColumn<Devolucion, String> colTipo;
    @FXML private TableColumn<Devolucion, String> colMonto;
    @FXML private TableColumn<Devolucion, String> colUsuario;
    @FXML private TableColumn<Devolucion, String> colEstado;
    @FXML private TableColumn<Devolucion, String> colMotivo;
    @FXML private TextField txtBuscar;
    @FXML private TextField txtFacturaNueva;
    @FXML private Label lblMensaje;
    @FXML private Label lblMensajeNueva;

    private final DevolucionService devolucionService;
    private final VentaService ventaService;
    private final SpringFXMLLoader fxmlLoader;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Paginador<Devolucion> paginador;

    @FXML
    public void initialize() {
        configurarColumnas();
        paginador = new Paginador<>(tablaDevoluciones);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaDevoluciones.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) -> filtrar(val));
        cargar();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty("#" + d.getValue().getIdDevolucion()));
        colFactura.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getVenta().getNumeroFactura()));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colProductos.setCellValueFactory(d -> {
            String resumen = d.getValue().getDetalles().stream()
                    .map(det -> det.getProducto().getNombre() + " x" + det.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen.isEmpty() ? "—" : resumen);
        });
        colTipo.setCellValueFactory(d -> new SimpleStringProperty(
                switch (d.getValue().getTipoDevolucion()) {
                    case SALDO_A_FAVOR -> "Saldo a favor";
                    case NOTA_CREDITO -> "Nota de crédito";
                }));
        colMonto.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getMontoTotal().toPlainString()));
        colUsuario.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getUsuario().getNombreCompleto()));
        colMotivo.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getMotivo()));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                boolean anulada = getTableRow().getItem().getEstado()
                        == com.jordis.jordis.model.EstadoDevolucion.ANULADA;
                Label badge = new Label(anulada ? "Anulada" : "Registrada");
                badge.setStyle("-fx-background-color: " + (anulada ? "#FEE2E2" : "#DCFCE7")
                        + "; -fx-text-fill: " + (anulada ? "#DC2626" : "#15803D")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });
    }

    private void cargar() {
        paginador.setDatos(devolucionService.obtenerTodas());
    }

    private void filtrar(String texto) {
        List<Devolucion> base = devolucionService.obtenerTodas();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base);
            return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(d -> d.getVenta().getNumeroFactura() != null
                        && d.getVenta().getNumeroFactura().toLowerCase().contains(t))
                .toList());
    }

    /**
     * Registrar una devolución directamente desde este módulo, sin tener
     * que ir primero a la lista de Ventas: se busca la venta por número
     * de factura y, si tiene productos disponibles para devolver, se abre
     * el mismo formulario de devolución que se usa desde Ventas.
     */
    @FXML
    public void onNuevaDevolucion() {
        lblMensajeNueva.setText("");
        String numeroFactura = txtFacturaNueva.getText() != null
                ? txtFacturaNueva.getText().trim() : "";

        if (numeroFactura.isEmpty()) {
            mostrarMensajeNueva("Escribe el número de factura de la venta a devolver.", true);
            return;
        }

        Optional<Venta> ventaOpt = ventaService.buscarPorNumeroFactura(numeroFactura);
        if (ventaOpt.isEmpty()) {
            mostrarMensajeNueva("No se encontró ninguna venta con la factura \""
                    + numeroFactura + "\".", true);
            return;
        }

        Venta venta = ventaOpt.get();
        if (venta.estaAnulada()) {
            mostrarMensajeNueva("Esa venta ya está anulada — no se le pueden "
                    + "registrar devoluciones.", true);
            return;
        }

        boolean quedaAlgoPorDevolver = venta.getDetalles().stream()
                .anyMatch(det -> devolucionService
                        .obtenerCantidadDisponibleParaDevolver(det) > 0);
        if (!quedaAlgoPorDevolver) {
            mostrarMensajeNueva("Esa venta ya no tiene productos disponibles "
                    + "para devolver (ya se devolvió todo).", true);
            return;
        }

        abrirFormularioDevolucion(venta);
    }

    private void abrirFormularioDevolucion(Venta venta) {
        try {
            SpringFXMLLoader.LoadResult<DevolucionFormController> result =
                    fxmlLoader.loadWithController("/fxml/devolucion_form.fxml");

            result.controller.prepararParaVenta(venta);
            result.controller.setOnGuardado(() -> {
                cargar();
                txtFacturaNueva.clear();
                mostrarMensajeNueva("", false);
                mostrarMensaje("Devolución registrada. Stock actualizado.", false);
            });

            Stage stage = VentanaUtil.crearDialogoModal(
                    result.root, "Registrar devolución", 720, 620);
            result.controller.setStage(stage);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de devolución", e);
            mostrarMensajeNueva("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void mostrarMensajeNueva(String texto, boolean esError) {
        lblMensajeNueva.setText(texto);
        lblMensajeNueva.setStyle("-fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}