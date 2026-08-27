package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.service.FacturaService;
import com.jordis.jordis.service.VentaService;
import com.jordis.jordis.util.PaginadorRemoto;
import javafx.beans.property.SimpleStringProperty;
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

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class VentaController {

    @FXML private TableView<Venta> tablaVentas;
    @FXML private TableColumn<Venta, String> colId;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colProductos;
    @FXML private TableColumn<Venta, String> colSubtotal;
    @FXML private TableColumn<Venta, String> colDescuento;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colPago;
    @FXML private TableColumn<Venta, String> colEstado;
    @FXML private TableColumn<Venta, Void>   colAcciones;
    @FXML private Label lblMensaje;
    @FXML private TableColumn<Venta, String> colNcf;

    @FXML private TextField txtBuscarFactura;

    private final VentaService ventaService;
    private final SpringFXMLLoader fxmlLoader;
    private final FacturaService facturaService;
    private PaginadorRemoto<Venta> paginador;

    // Debounce: evita disparar una consulta a la base de datos por cada
    // tecla presionada en el buscador — espera un instante de pausa.
    private final javafx.animation.PauseTransition debounceBusqueda =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarColumnas();
        paginador = new PaginadorRemoto<>(tablaVentas);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaVentas.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        debounceBusqueda.setOnFinished(e -> aplicarProveedorDePagina());
        txtBuscarFactura.textProperty().addListener((obs, old, val) ->
                debounceBusqueda.playFromStart());

        aplicarProveedorDePagina();
    }

    /**
     * Cada página se pide a la base de datos en el momento (LIMIT/OFFSET
     * real), con el texto de búsqueda actual — nunca se carga la tabla
     * de ventas completa en memoria, sin importar cuántas ventas haya.
     */
    private void aplicarProveedorDePagina() {
        String texto = txtBuscarFactura.getText() != null
                ? txtBuscarFactura.getText().trim() : "";
        paginador.setProveedor(pagina -> ventaService.obtenerPaginaVentas(pagina, texto));
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d -> {
            Venta v = d.getValue();
            String factura = v.getNumeroFactura();
            return new SimpleStringProperty(
                    factura != null && !factura.isBlank()
                            ? factura : "#" + v.getIdVenta());
        });
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getFechaHora().format(FMT)));
        colCliente.setCellValueFactory(d -> {
            var cliente = d.getValue().getCliente();
            return new SimpleStringProperty(
                    cliente != null ? cliente.getNombreCompleto() : "Ocasional");
        });
        colProductos.setCellValueFactory(d -> {
            String resumen = d.getValue().getDetalles().stream()
                    .map(vp -> vp.getProducto().getNombre() + " x" + vp.getCantidad())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(resumen.isEmpty() ? "—" : resumen);
        });
        colNcf.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); setText(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();
                if (v.getNcf() == null) {
                    setText("—"); setGraphic(null); return;
                }
                Label badge = new Label(v.getNcf());
                badge.setStyle(
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                                + " -fx-padding: 2 6; -fx-background-radius: 4;"
                                + " -fx-font-size: 10; -fx-font-weight: bold;");
                setGraphic(badge);
                setText(null);
            }
        });
        colSubtotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getSubtotal().toPlainString()));

        colDescuento.setCellValueFactory(d -> {
            var desc = d.getValue().getDescuentoPorcentual();
            return new SimpleStringProperty(
                    desc != null && desc.compareTo(java.math.BigDecimal.ZERO) > 0
                            ? desc.toPlainString() + "%" : "—");
        });
        colTotal.setCellValueFactory(d ->
                new SimpleStringProperty("RD$" + d.getValue().getTotal().toPlainString()));
        colPago.setCellValueFactory(d ->
                new SimpleStringProperty(
                        com.jordis.jordis.util.TextoFormateador.humanizar(d.getValue().getMetodoPago())));

        // Estado: Válida / Anulada — la venta anulada ya NO desaparece del
        // listado, se distingue con este badge en color.
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                boolean anulada = ((Venta) getTableRow().getItem()).estaAnulada();
                Label badge = new Label(anulada ? "Anulada" : "Válida");
                badge.setStyle("-fx-background-color: " + (anulada ? "#FEE2E2" : "#DCFCE7")
                        + "; -fx-text-fill: " + (anulada ? "#DC2626" : "#15803D")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        // Menú de acciones por fila (Ver factura / Registrar devolución /
        // Anular venta / Reimprimir factura), en vez de un solo botón.
        colAcciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Venta v = (Venta) getTableRow().getItem();

                MenuButton menu = new MenuButton("⋮ Acciones");
                menu.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                        + " -fx-border-color: #BFDBFE; -fx-border-radius: 4;"
                        + " -fx-background-radius: 4; -fx-font-size: 10;"
                        + " -fx-padding: 3 8; -fx-cursor: hand;");

                MenuItem miVerFactura = new MenuItem("Ver factura");
                miVerFactura.setOnAction(e -> verFactura(v));

                MenuItem miDevolucion = new MenuItem("Registrar devolución");
                miDevolucion.setDisable(v.estaAnulada());
                miDevolucion.setOnAction(e -> abrirDevolucion(v));

                MenuItem miAnular = new MenuItem("Anular venta");
                miAnular.setDisable(v.estaAnulada());
                miAnular.setOnAction(e -> anularVenta(v));

                MenuItem miReimprimir = new MenuItem("Reimprimir factura (80mm)");
                miReimprimir.setOnAction(e -> reimprimirTicket(v));

                menu.getItems().addAll(miVerFactura, miDevolucion, miAnular, miReimprimir);
                setGraphic(menu);
            }
        });
    }

    /** Recarga solo la página que se está viendo — no la tabla entera. */
    private void refrescarPaginaActual() {
        paginador.recargar();
    }

    @FXML
    public void onNuevaVenta() {
        try {
            SpringFXMLLoader.LoadResult<VentaFormController> result =
                    fxmlLoader.loadWithController("/fxml/venta_form.fxml");

            // Limpiar el formulario antes de mostrar (fix singleton)
            result.controller.prepararNuevaVenta();

            result.controller.setOnGuardado(() -> {
                refrescarPaginaActual();
                mostrarMensaje("Venta registrada correctamente.", false);
            });
            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Nueva Venta", 950, 800);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de venta", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    @FXML
    public void onBuscarFactura() {
        aplicarProveedorDePagina();
    }

    @FXML
    public void onVerTodas() {
        txtBuscarFactura.clear();
        lblMensaje.setText("");
        aplicarProveedorDePagina();
    }

    private void verFactura(Venta venta) {
        try {
            String ruta = facturaService.generarFactura(venta);
            facturaService.abrirPDF(ruta);
        } catch (Exception e) {
            log.error("Error generando factura", e);
            mostrarMensaje("Error al generar factura: " + e.getMessage(), true);
        }
    }

    private void reimprimirTicket(Venta venta) {
        try {
            String ruta = facturaService.generarTicket80mm(venta);
            facturaService.abrirPDF(ruta);
        } catch (Exception e) {
            log.error("Error generando ticket 80mm", e);
            mostrarMensaje("Error al generar el ticket: " + e.getMessage(), true);
        }
    }

    private void abrirDevolucion(Venta venta) {
        try {
            SpringFXMLLoader.LoadResult<DevolucionFormController> result =
                    fxmlLoader.loadWithController("/fxml/devolucion_form.fxml");

            result.controller.prepararParaVenta(venta);
            result.controller.setOnGuardado(() -> {
                refrescarPaginaActual();
                mostrarMensaje("Devolución registrada. Stock actualizado.", false);
            });

            Stage stage = com.jordis.jordis.util.VentanaUtil.crearDialogoModal(
                    result.root, "Registrar devolución", 720, 620);
            result.controller.setStage(stage);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de devolución", e);
            mostrarMensaje("Error al abrir: " + e.getMessage(), true);
        }
    }

    private void anularVenta(Venta venta) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Anular Venta");
        stage.setResizable(true);
        stage.setMinWidth(460);
        stage.setMinHeight(400);

        javafx.scene.text.Text titulo = new javafx.scene.text.Text("Anular Venta");
        titulo.setStyle("-fx-font-size: 16; -fx-font-weight: bold;"
                + " -fx-fill: #DC2626;");

        Label lblFactura = new Label(
                (venta.getNumeroFactura() != null
                        ? "Factura " + venta.getNumeroFactura()
                        : "Venta #" + venta.getIdVenta())
                        + "  ·  " + (venta.getCliente() != null
                        ? venta.getCliente().getNombreCompleto() : "Cliente ocasional")
                        + "  ·  RD$" + String.format("%,.2f", venta.getTotal().doubleValue()));
        lblFactura.setStyle("-fx-font-size: 13; -fx-text-fill: #374151;");
        lblFactura.setWrapText(true);

        ToggleGroup grupoTipoAnulacion = new ToggleGroup();
        RadioButton rbErrorCajero = new RadioButton("Error del cajero o administrativo");
        rbErrorCajero.setToggleGroup(grupoTipoAnulacion);
        rbErrorCajero.setSelected(true);
        rbErrorCajero.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");

        RadioButton rbProblemaProducto = new RadioButton(
                "Problema con el producto (equivale a una devolución completa)");
        rbProblemaProducto.setToggleGroup(grupoTipoAnulacion);
        rbProblemaProducto.setWrapText(true);
        rbProblemaProducto.setStyle("-fx-font-size: 12; -fx-text-fill: #374151;");

        VBox vTipoAnulacion = new VBox(6,
                new Label("Tipo de anulación *") {{
                    setStyle("-fx-font-size: 12; -fx-text-fill: #64748B; -fx-font-weight: bold;");
                }},
                rbErrorCajero, rbProblemaProducto);

        TextArea txtMotivo = new TextArea();
        txtMotivo.setPromptText("Explica qué pasó exactamente...");
        txtMotivo.setPrefHeight(90);
        txtMotivo.setWrapText(true);
        txtMotivo.setStyle("-fx-font-size: 13; -fx-border-color: #FCA5A5;"
                + " -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 10;");

        Label lblError = new Label("");
        lblError.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12;");
        lblError.setWrapText(true);

        Button btnAnularConfirmar = new Button("Anular venta");
        btnAnularConfirmar.setPrefHeight(38);
        btnAnularConfirmar.setPrefWidth(160);
        btnAnularConfirmar.setStyle("-fx-background-color: #DC2626;"
                + " -fx-text-fill: white; -fx-font-size: 13;"
                + " -fx-background-radius: 6; -fx-cursor: hand;");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setPrefHeight(38);
        btnCancelar.setPrefWidth(120);
        btnCancelar.setStyle("-fx-background-color: transparent;"
                + " -fx-text-fill: #64748B; -fx-border-color: #E2E8F0;"
                + " -fx-border-radius: 6; -fx-background-radius: 6;"
                + " -fx-font-size: 13; -fx-cursor: hand;");

        btnCancelar.setOnAction(e -> stage.close());
        btnAnularConfirmar.setOnAction(e -> {
            String motivo = txtMotivo.getText().trim();
            if (motivo.isEmpty()) {
                lblError.setText("Debes ingresar el detalle de la anulación.");
                return;
            }

            boolean porProblemaProducto = rbProblemaProducto.isSelected();

            java.math.BigDecimal montoCobrado = Boolean.TRUE.equals(venta.getEsCredito())
                    ? venta.getTotalPagado() : venta.getTotal();
            boolean creditoConAbonos = Boolean.TRUE.equals(venta.getEsCredito())
                    && venta.getTotalPagado().compareTo(java.math.BigDecimal.ZERO) > 0;
            boolean generaraNotaCredito = (porProblemaProducto || creditoConAbonos)
                    && montoCobrado.compareTo(java.math.BigDecimal.ZERO) > 0;

            if (generaraNotaCredito && venta.getCliente() == null) {
                lblError.setText("Esta anulación generaría saldo a favor, pero la venta no "
                        + "tiene un cliente identificado. Asigna un cliente a la venta primero.");
                return;
            }

            if (generaraNotaCredito) {
                String nombreCliente = venta.getCliente() != null
                        ? venta.getCliente().getNombreCompleto() : "el cliente";
                Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
                confirmacion.setTitle("Confirmar anulación");
                confirmacion.setHeaderText("¿Seguro que quieres anular esta venta?");
                confirmacion.setContentText("A " + nombreCliente + " le van a quedar RD$"
                        + montoCobrado.toPlainString()
                        + " de saldo a favor por lo ya cobrado en esta venta.");
                confirmacion.initOwner(stage);
                var resultado = confirmacion.showAndWait();
                if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
                    return;
                }
            }

            try {
                Venta anulada = ventaService.anularVenta(
                        venta.getIdVenta(), motivo, porProblemaProducto);
                refrescarPaginaActual();

                if (anulada.getNcfNotaCreditoAnulacion() != null
                        || (generaraNotaCredito)) {
                    String detalleNcf = anulada.getNcfNotaCreditoAnulacion() != null
                            ? " (Nota de Crédito " + anulada.getNcfNotaCreditoAnulacion() + ")"
                            : "";
                    mostrarMensaje("Venta anulada. Stock restaurado. RD$"
                                    + montoCobrado.toPlainString()
                                    + " quedaron como saldo a favor del cliente" + detalleNcf + ".",
                            false);
                } else {
                    mostrarMensaje("Venta anulada. Stock restaurado.", false);
                }
                stage.close();
            } catch (Exception ex) {
                lblError.setText("Error: " + ex.getMessage());
            }
        });

        VBox vMotivo = new VBox(6,
                new Label("Detalle / explicación *") {{
                    setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");
                }},
                txtMotivo);

        HBox botones = new HBox(10, btnAnularConfirmar, btnCancelar);
        botones.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox root = new VBox(16, titulo, lblFactura, vTipoAnulacion, vMotivo, lblError, botones);
        root.setStyle("-fx-background-color: white; -fx-padding: 28;");
        root.setPrefWidth(460);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white; -fx-background: white;");

        stage.setScene(new Scene(scroll, 460, 400));
        stage.showAndWait();
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}