package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.service.ClienteService;
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
    @FXML private TableColumn<Cliente, String> colApellido;
    @FXML private TableColumn<Cliente, String> colCedula;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colDireccion;
    @FXML private TableColumn<Cliente, Void>   colAcciones;
    @FXML private TextField txtBuscar;
    @FXML private Label lblMensaje;

    private final ClienteService clienteService;
    private final SpringFXMLLoader fxmlLoader;

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarClientes();
    }

    private void configurarColumnas() {
        colId.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getIdCliente())));
        colNombre.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getNombre()));
        colApellido.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getApellido()));
        colCedula.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getCedulaIdentificacion()));
        colTelefono.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getTelefono() != null ? d.getValue().getTelefono() : "—"));
        colDireccion.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDireccion() != null ? d.getValue().getDireccion() : "—"));

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar   = crearBtn("Editar",   "#2563EB", "#EFF6FF");
            private final Button btnEliminar = crearBtn("Eliminar", "#DC2626", "#FEF2F2");
            private final HBox box = new HBox(6, btnEditar, btnEliminar);

            {
                btnEditar.setOnAction(e ->
                        abrirFormulario(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(e ->
                        eliminar(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
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
        tablaClientes.setItems(
                FXCollections.observableArrayList(clienteService.obtenerTodos()));
    }

    @FXML
    public void onNuevoCliente() {
        abrirFormulario(null);
    }

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
        if (resultado.isEmpty()) {
            mostrarMensaje("No se encontraron clientes.", true);
        } else {
            lblMensaje.setText("");
        }
    }

    @FXML
    public void onVerTodos() {
        cargarClientes();
        txtBuscar.clear();
        lblMensaje.setText("");
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
            stage.setScene(new Scene(result.root, 500, 360));
            stage.showAndWait();

        } catch (Exception e) {
            log.error("Error abriendo formulario de cliente", e);
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 0 24 10 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}