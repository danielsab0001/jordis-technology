package com.jordis.jordis.controller;

import com.jordis.jordis.model.AjusteInventario;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.repository.AjusteInventarioRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class AjusteHistorialController {

    @FXML private Label lblProducto;
    @FXML private Label lblStockActual;
    @FXML private TableView<AjusteInventario> tablaAjustes;
    @FXML private TableColumn<AjusteInventario, String> colFecha;
    @FXML private TableColumn<AjusteInventario, String> colUsuario;
    @FXML private TableColumn<AjusteInventario, String> colTipo;
    @FXML private TableColumn<AjusteInventario, String> colCantidad;
    @FXML private TableColumn<AjusteInventario, String> colMotivo;

    private final AjusteInventarioRepository ajusteRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        colFecha.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getFechaHora().format(FMT)));
        colUsuario.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getUsuario().getNombreCompleto()));

        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                int cant = ((AjusteInventario) getTableRow().getItem())
                        .getCantidad();
                Label badge = new Label(cant > 0 ? "Entrada" : "Salida");
                badge.setStyle("-fx-background-color: "
                        + (cant > 0 ? "#DCFCE7" : "#FEE2E2")
                        + "; -fx-text-fill: "
                        + (cant > 0 ? "#15803D" : "#DC2626")
                        + "; -fx-padding: 2 8; -fx-background-radius: 4;"
                        + " -fx-font-size: 11; -fx-font-weight: bold;");
                setGraphic(badge);
            }
        });

        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                int cant = ((AjusteInventario) getTableRow().getItem())
                        .getCantidad();
                setText((cant > 0 ? "+" : "") + cant);
                setStyle(cant > 0
                        ? "-fx-text-fill: #15803D; -fx-font-weight: bold;"
                        : "-fx-text-fill: #DC2626; -fx-font-weight: bold;");
            }
        });

        colMotivo.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getMotivo() != null
                                ? d.getValue().getMotivo() : "—"));
    }

    public void setProducto(Producto producto) {
        lblProducto.setText(producto.getNombre()
                + (producto.getMarca() != null
                ? " — " + producto.getMarca() : ""));
        lblStockActual.setText(
                "Stock actual: " + producto.getStock() + " unidades");
        tablaAjustes.setItems(FXCollections.observableArrayList(
                ajusteRepository.findByProducto(producto.getIdProducto())));
    }

    @FXML
    public void onCerrar() {
        ((Stage) tablaAjustes.getScene().getWindow()).close();
    }
}