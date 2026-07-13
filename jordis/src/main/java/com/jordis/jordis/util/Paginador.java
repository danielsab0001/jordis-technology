package com.jordis.jordis.util;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

public class Paginador<T> {

    private static final int FILAS_POR_PAGINA = 15;

    private final TableView<T> tabla;
    private final Label        lblInfo;
    private final Button       btnAnterior;
    private final Button       btnSiguiente;
    private final HBox         barraNavegacion;

    private List<T> datos        = List.of();
    private int     paginaActual = 0;

    public Paginador(TableView<T> tabla) {
        this.tabla = tabla;

        btnAnterior  = crearBtn("← Anterior");
        btnSiguiente = crearBtn("Siguiente →");
        lblInfo      = new Label();
        lblInfo.setStyle("-fx-font-size: 12; -fx-text-fill: #64748B;");

        btnAnterior.setOnAction(e  -> irAPagina(paginaActual - 1));
        btnSiguiente.setOnAction(e -> irAPagina(paginaActual + 1));

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        barraNavegacion = new HBox(10, espacio, lblInfo, btnAnterior, btnSiguiente);
        barraNavegacion.setAlignment(Pos.CENTER_RIGHT);
        barraNavegacion.setStyle("-fx-padding: 8 24;");
    }

    public HBox getBarraNavegacion() { return barraNavegacion; }

    public void setDatos(List<T> datos) {
        this.datos        = datos;
        this.paginaActual = 0;
        actualizar();
    }

    private void irAPagina(int pagina) {
        int totalPaginas = getTotalPaginas();
        if (pagina < 0 || pagina >= totalPaginas) return;
        paginaActual = pagina;
        actualizar();
    }

    private void actualizar() {
        int total     = datos.size();
        int totalPags = getTotalPaginas();
        int desde     = paginaActual * FILAS_POR_PAGINA;
        int hasta     = Math.min(desde + FILAS_POR_PAGINA, total);

        // Limpiar primero para forzar que updateItem se dispare
        tabla.setItems(FXCollections.observableArrayList());
        tabla.setItems(FXCollections.observableArrayList(
                datos.subList(desde, hasta)));

        // Forzar repintado de todas las celdas
        tabla.refresh();

        lblInfo.setText("Página " + (paginaActual + 1)
                + " de " + Math.max(totalPags, 1)
                + "  (" + total + " registros)");

        btnAnterior.setDisable(paginaActual == 0);
        btnSiguiente.setDisable(
                paginaActual >= totalPags - 1 || totalPags <= 1);
    }

    private int getTotalPaginas() {
        return (int) Math.ceil((double) datos.size() / FILAS_POR_PAGINA);
    }

    private Button crearBtn(String texto) {
        Button btn = new Button(texto);
        btn.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB;"
                + " -fx-border-color: #BFDBFE; -fx-border-radius: 6;"
                + " -fx-background-radius: 6; -fx-font-size: 12;"
                + " -fx-padding: 5 12; -fx-cursor: hand;");
        return btn;
    }
}