package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.service.ClienteService;
import com.jordis.jordis.util.Paginador;
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
public class ClienteController {

    @FXML private TableView<Cliente> tablaClientes;
    @FXML private TableColumn<Cliente, String> colId;
    @FXML private TableColumn<Cliente, String> colNombre;
    @FXML private TableColumn<Cliente, String> colTipo;
    @FXML private TableColumn<Cliente, String> colIdentificador;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colContacto;
    @FXML private TableColumn<Cliente, String> colDireccion;
    @FXML private TableColumn<Cliente, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final ClienteService clienteService;
    private final SpringFXMLLoader fxmlLoader;
    private Paginador<Cliente> paginador;

    @FXML
    public void initialize() {
        configurarColumnas();

        paginador = new Paginador<>(tablaClientes);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.VBox padre =
                    (javafx.scene.layout.VBox) tablaClientes.getParent();
            if (padre != null && !padre.getChildren()
                    .contains(paginador.getBarraNavegacion())) {
                padre.getChildren().add(paginador.getBarraNavegacion());
            }
        });

        txtBuscar.textProperty().addListener((obs, old, val) -> filtrarClientes(val));

        cargarClientes();
    }

    private void filtrarClientes(String texto) {
        List<Cliente> base = clienteService.obtenerTodos();
        if (texto == null || texto.isBlank()) {
            paginador.setDatos(base);
            return;
        }
        String t = texto.toLowerCase();
        paginador.setDatos(base.stream()
                .filter(c -> c.getNombreCompleto().toLowerCase().contains(t)
                        || c.getIdentificador().toLowerCase().contains(t)
                        || (c.getTelefono() != null &&
                        c.getTelefono().toLowerCase().contains(t)))
                .toList());
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdCliente())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombreCompleto()));
        colTipo.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getTipoCliente()));
        colIdentificador.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getIdentificador()));
        colTelefono.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getTelefono() != null ? d.getValue().getTelefono() : "—"));
        colContacto.setCellValueFactory(d -> {
            Cliente c = d.getValue();
            if (c.esEmpresa() && c.getContactoPrincipal() != null
                    && !c.getContactoPrincipal().isBlank()) {
                return new SimpleStringProperty(c.getContactoPrincipal());
            }
            return new SimpleStringProperty("—");
        });
        colDireccion.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDireccion() != null ? d.getValue().getDireccion() : "—"));

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar   = crearBtn("Editar",   "#2563EB", "#EFF6FF");
            private final Button btnHistorial = crearBtn("Historial", "#6D28D9", "#EDE9FE");
            private final Button btnEliminar = crearBtn("Eliminar", "#DC2626", "#FEF2F2");
            private final HBox box = new HBox(5, btnEditar, btnHistorial, btnEliminar);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Cliente cliente = (Cliente) getTableRow().getItem();
                btnEditar.setOnAction(e -> abrirFormulario(cliente));
                btnHistorial.setOnAction(e -> abrirHistorial(cliente));
                btnEliminar.setOnAction(e -> eliminar(cliente));
                setGraphic(box);
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

    private void cargarClientes() {
        paginador.setDatos(clienteService.obtenerTodos());
    }

    @FXML public void onNuevoCliente() { abrirFormulario(null); }

    @FXML
    public void onBuscar() {
        String texto = txtBuscar.getText().trim();
        List<Cliente> resultado;
        try {
            resultado = List.of(clienteService.buscarPorCedula(texto));
        } catch (ClienteService.ClienteNoEncontradoException e) {
            resultado = clienteService.buscar(texto);
        }
        tablaClientes.setItems(FXCollections.observableArrayList(resultado));
        mostrarMensaje(resultado.isEmpty() ? "No se encontraron clientes." : "", resultado.isEmpty());
    }

    @FXML
    public void onVerTodos() {
        txtBuscar.clear();
    }

    private void eliminar(Cliente cliente) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar a " + cliente.getNombreCompleto() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                clienteService.eliminar(cliente.getIdCliente());
                cargarClientes();
                mostrarMensaje("Cliente eliminado correctamente.", false);
            }
        });
    }

    private void abrirFormulario(Cliente cliente) {
        try {
            SpringFXMLLoader.LoadResult<ClienteFormController> result =
                    fxmlLoader.loadWithController("/fxml/cliente_form.fxml");
            result.controller.setCliente(cliente);
            result.controller.setOnGuardado(() -> {
                cargarClientes();
                mostrarMensaje("Cliente guardado correctamente.", false);
            });
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(cliente == null ? "Nuevo Cliente" : "Editar Cliente");
            stage.setScene(new Scene(result.root, 520, 420));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo formulario de cliente", e);
            mostrarMensaje("Error al abrir el formulario.", true);
        }
    }

    private void abrirHistorial(Cliente cliente) {
        try {
            SpringFXMLLoader.LoadResult<ClienteHistorialController> result =
                    fxmlLoader.loadWithController("/fxml/cliente_historial.fxml");
            result.controller.setCliente(cliente);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Historial — " + cliente.getNombreCompleto());
            stage.setScene(new Scene(result.root, 750, 520));
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo historial de cliente", e);
            mostrarMensaje("Error al abrir historial.", true);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}