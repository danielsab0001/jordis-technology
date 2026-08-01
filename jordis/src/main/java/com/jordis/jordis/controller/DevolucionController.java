package com.jordis.jordis.controller;

import com.jordis.jordis.model.Devolucion;
import com.jordis.jordis.service.DevolucionService;
import com.jordis.jordis.util.Paginador;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
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
    @FXML private Label lblMensaje;

    private final DevolucionService devolucionService;

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
}