package com.jordis.jordis.controller;

import com.jordis.jordis.model.AuditoriaLog;
import com.jordis.jordis.service.AuditoriaService;
import com.jordis.jordis.util.Paginador;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditoriaController {

    @FXML private TextField txtBuscar;
    @FXML private ComboBox<String> cmbAccion;
    @FXML private TableView<AuditoriaLog> tabla;
    @FXML private TableColumn<AuditoriaLog, String> colFecha;
    @FXML private TableColumn<AuditoriaLog, String> colUsuario;
    @FXML private TableColumn<AuditoriaLog, String> colAccion;
    @FXML private TableColumn<AuditoriaLog, String> colEntidad;
    @FXML private TableColumn<AuditoriaLog, String> colDetalle;
    @FXML private Label lblMensaje;

    private final AuditoriaService auditoriaService;
    private Paginador<AuditoriaLog> paginador;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFechaHora() != null
                        ? d.getValue().getFechaHora().format(FMT) : ""));
        colUsuario.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getUsuario() != null
                        ? d.getValue().getUsuario().getNombreCompleto() : "Sistema"));
        colAccion.setCellValueFactory(d -> new SimpleStringProperty(
                etiquetaAccion(d.getValue().getAccion())));
        colEntidad.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getEntidad() != null
                        ? d.getValue().getEntidad()
                        + (d.getValue().getIdEntidad() != null
                        ? " #" + d.getValue().getIdEntidad() : "")
                        : ""));
        colDetalle.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getDetalle() != null ? d.getValue().getDetalle() : ""));
        colDetalle.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
            }
        });

        paginador = new Paginador<>(tabla);
        tabla.sceneProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            VBox padre = (VBox) tabla.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        cmbAccion.getItems().addAll(null,
                "VENTA_REGISTRADA", "VENTA_ANULADA", "DEVOLUCION_REGISTRADA",
                "COMPRA_REGISTRADA",
                "CLIENTE_CREADO", "PRODUCTO_CREADO", "PROVEEDOR_CREADO",
                "USUARIO_CREADO", "USUARIO_DESBLOQUEADO",
                "PRECIO_MODIFICADO", "INVENTARIO_AJUSTADO",
                "CAJA_ABIERTA", "CIERRE_CAJA");
        cmbAccion.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String s) {
                return s == null ? "Todas las acciones" : etiquetaAccion(s);
            }
            @Override public String fromString(String s) { return null; }
        });
        cmbAccion.setOnAction(e -> aplicarFiltros());
        txtBuscar.textProperty().addListener((obs, old, val) -> aplicarFiltros());

        cargarRegistros();
    }

    private void cargarRegistros() {
        paginador.setDatos(auditoriaService.obtenerTodas());
    }

    @FXML
    public void onActualizar() {
        cargarRegistros();
        lblMensaje.setText("Actualizado.");
    }

    private void aplicarFiltros() {
        String texto = txtBuscar.getText() == null
                ? "" : txtBuscar.getText().trim().toLowerCase();
        String accion = cmbAccion.getValue();

        List<AuditoriaLog> filtrados = auditoriaService.obtenerTodas().stream()
                .filter(a -> accion == null || accion.equals(a.getAccion()))
                .filter(a -> texto.isEmpty()
                        || contiene(a.getAccion(), texto)
                        || contiene(a.getDetalle(), texto)
                        || (a.getUsuario() != null
                        && contiene(a.getUsuario().getNombreCompleto(), texto)))
                .toList();
        paginador.setDatos(filtrados);
    }

    private boolean contiene(String texto, String busqueda) {
        return texto != null && texto.toLowerCase().contains(busqueda);
    }

    private String etiquetaAccion(String accion) {
        return com.jordis.jordis.util.TextoFormateador.etiquetaAccion(accion);
    }
}
