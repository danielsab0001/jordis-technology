package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Compra;
import com.jordis.jordis.service.CompraService;
import com.jordis.jordis.service.CuentaPorPagarService;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompraController {

    @FXML private TableView<Compra> tablaCompras;
    @FXML private TableColumn<Compra, String> colId;
    @FXML private TableColumn<Compra, String> colFecha;
    @FXML private TableColumn<Compra, String> colProveedor;
    @FXML private TableColumn<Compra, String> colProductos;
    @FXML private TableColumn<Compra, String> colTotal;
    @FXML private TableColumn<Compra, String> colEstado;
    @FXML private TableColumn<Compra, Void>   colAcciones;
    @FXML private Label lblMensaje;

    private final CompraService compraService;
    private final SpringFXMLLoader fxmlLoader;
    private final CuentaPorPagarService cuentaPorPagarService;
    private Paginador<Compra> paginador;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarColumnas();

        paginador = new Paginador<>(tablaCompras);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaCompras.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        cargarCompras();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdCompra())));
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaPedido().format(FMT)));
        colProveedor.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getProveedor().getNombre()));

        colProductos.setCellValueFactory(d -> {
            String resumen = d.getValue().getDetalles().stream()
                    .map(det -> det.getProducto().getNombre() + " x" + det.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen.isEmpty() ? "—" : resumen);
        });

        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" +
                        d.getValue().getTotalCompra().toPlainString()));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                String estado = ((Compra) getTableRow().getItem()).getEstado();
                String fondo = switch (estado) {
                    case "PENDIENTE" -> "#FEF3C7";
                    case "RECIBIDA" -> "#DCFCE7";
                    default -> "#FEE2E2";
                };
                String color = switch (estado) {
                    case "PENDIENTE" -> "#B45309";
                    case "RECIBIDA" -> "#15803D";
                    default -> "#DC2626";
                };
                Label badge = new Label(estado);
                badge.setStyle("-fx-background-color: " + fondo
                        + "; -fx-text-fill: " + color
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Compra c = (Compra) getTableRow().getItem();
                boolean pendiente = "PENDIENTE".equals(c.getEstado());
                boolean cancelada = "CANCELADA".equals(c.getEstado());

                MenuButton menu = new MenuButton("⋮ Acciones");
                menu.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10;"
                        + " -fx-padding: 3 8; -fx-cursor: hand;");

                MenuItem miVer = new MenuItem("Ver detalle");
                miVer.setDisable(cancelada);
                miVer.setOnAction(e -> verDetalleCompra(c));

                MenuItem miRecibir = new MenuItem("Recibir");
                miRecibir.setDisable(!pendiente);
                miRecibir.setOnAction(e -> recibirCompra(c));

                MenuItem miEditar = new MenuItem("Editar");
                miEditar.setDisable(!pendiente);
                miEditar.setOnAction(e -> abrirEdicion(c));

                MenuItem miCancelar = new MenuItem("Cancelar compra");
                miCancelar.setDisable(!pendiente);
                miCancelar.setOnAction(e -> cancelarCompra(c));

                menu.getItems().addAll(miVer, miRecibir, miEditar, miCancelar);
                setGraphic(menu);
            }
        });
    }

    private void abrirEdicion(Compra compra) {
        try {
            SpringFXMLLoader.LoadResult<CompraEdicionFormController> result =
                    fxmlLoader.loadWithController("/fxml/compra_edicion_form.fxml");
            result.controller.setCompra(compra);
            result.controller.setOnGuardado(() -> {
                cargarCompras();
                mostrarMensaje("Compra actualizada correctamente.", false);
            });
            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Editar Compra #" + compra.getIdCompra(), 680, 520);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo edición de compra", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void cargarCompras() {
        paginador.setDatos(compraService.obtenerTodas());
        lblMensaje.setText("");
    }

    @FXML public void onVerPendientes() {
        tablaCompras.setItems(
                FXCollections.observableArrayList(compraService.obtenerPendientes()));
        mostrarMensaje("Mostrando solo compras pendientes.", false);
    }

    @FXML public void onVerTodas() { cargarCompras(); }

    @FXML
    public void onNuevaCompra() {
        try {
            SpringFXMLLoader.LoadResult<CompraFormController> result =
                    fxmlLoader.loadWithController("/fxml/compra_form.fxml");

            // Limpiar el formulario antes de mostrar (fix singleton)
            result.controller.prepararNuevaCompra();

            result.controller.setOnGuardado(() -> {
                cargarCompras();
                mostrarMensaje("Compra registrada correctamente.", false);
            });
            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Nueva Compra", 700, 600);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de compra", e);
            mostrarMensaje("Error al abrir el formulario.", true);
        }
    }

    private void verDetalleCompra(Compra compra) {
        try {
            SpringFXMLLoader.LoadResult<CompraDetalleController> result =
                    fxmlLoader.loadWithController("/fxml/compra_detalle.fxml");
            result.controller.setCompra(compra);
            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Detalle Compra #" + compra.getIdCompra(), 760, 500);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo detalle de compra", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
        }

        private void recibirCompra(Compra compra) {
            // Diálogo para confirmar y opcionalmente crear cuenta por pagar
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Confirmar recepción");
            dialog.setHeaderText("¿Marcar la compra #" + compra.getIdCompra()
                    + " como recibida?\nEl stock se actualizará automáticamente.");

            CheckBox chkCrearCuenta = new CheckBox(
                    "Crear cuenta por pagar a " + compra.getProveedor().getNombre());
            chkCrearCuenta.setSelected(true);

            DatePicker dpFechaLimite = new DatePicker();
            dpFechaLimite.setPromptText("Fecha límite de pago");

            Label lblFecha = new Label("Fecha límite:");

            VBox contenido = new VBox(10,
                    chkCrearCuenta,
                    new HBox(10, lblFecha, dpFechaLimite));
            contenido.setStyle("-fx-padding: 16;");

            chkCrearCuenta.selectedProperty().addListener((obs, old, val) -> {
                lblFecha.setVisible(val);
                dpFechaLimite.setVisible(val);
            });

            dialog.getDialogPane().setContent(contenido);
            dialog.getDialogPane().getButtonTypes()
                    .addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    try {
                        Compra recibida = compraService.recibirCompra(compra.getIdCompra());

                        // Crear cuenta por pagar si se seleccionó
                        if (chkCrearCuenta.isSelected()) {
                            LocalDateTime fechaLimite = dpFechaLimite.getValue() != null
                                    ? dpFechaLimite.getValue().atTime(23, 59)
                                    : null;
                            cuentaPorPagarService.crearDesdCompra(
                                    recibida, fechaLimite, null);
                        }

                        cargarCompras();
                        mostrarMensaje("Compra recibida. Stock y cuentas actualizadas.",
                                false);
                    } catch (Exception e) {
                        mostrarMensaje("Error: " + e.getMessage(), true);
                    }
                }
            });
        }

    private void cancelarCompra(Compra compra) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Cancelar la compra #" + compra.getIdCompra() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar cancelación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                compraService.cancelarCompra(compra.getIdCompra());
                cargarCompras();
                mostrarMensaje("Compra cancelada.", false);
            }
        });
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}