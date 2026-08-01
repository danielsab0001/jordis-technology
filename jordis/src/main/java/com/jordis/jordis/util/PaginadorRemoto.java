package com.jordis.jordis.util;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.IntFunction;

/**
 * Igual que {@link Paginador} en apariencia (misma barra de navegación),
 * pero pensado para tablas que van a crecer mucho (ventas, en particular).
 * En vez de recibir la lista completa ya cargada y cortarla en memoria,
 * pide a un {@code IntFunction<Pagina<T>>} solo la página que necesita
 * mostrar — el LIMIT/OFFSET real ocurre en la base de datos, así que abrir
 * el módulo cuesta lo mismo con 50 filas que con 50,000.
 */
public class PaginadorRemoto<T> {

    private final TableView<T> tabla;
    private final Label        lblInfo;
    private final Button       btnAnterior;
    private final Button       btnSiguiente;
    private final HBox         barraNavegacion;

    private IntFunction<Pagina<T>> proveedor;
    private int paginaActual = 0;

    public PaginadorRemoto(TableView<T> tabla) {
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

    /**
     * Define de dónde salen los datos y va a la página 0. Llamar de nuevo
     * (por ejemplo al cambiar el texto de búsqueda) reinicia a la primera
     * página con el nuevo proveedor.
     */
    public void setProveedor(IntFunction<Pagina<T>> proveedor) {
        this.proveedor = proveedor;
        irAPagina(0);
    }

    /** Vuelve a pedir la página actual (por ejemplo, tras registrar/anular/devolver algo). */
    public void recargar() {
        irAPagina(paginaActual);
    }

    private void irAPagina(int pagina) {
        if (proveedor == null || pagina < 0) return;

        Pagina<T> resultado = proveedor.apply(pagina);
        paginaActual = resultado.totalRegistros() == 0 ? 0 : pagina;

        tabla.setItems(FXCollections.observableArrayList());
        tabla.setItems(FXCollections.observableArrayList(resultado.contenido()));
        tabla.refresh();

        lblInfo.setText("Página " + (paginaActual + 1)
                + " de " + Math.max(resultado.totalPaginas(), 1)
                + "  (" + resultado.totalRegistros() + " registros)");

        btnAnterior.setDisable(paginaActual == 0);
        btnSiguiente.setDisable(paginaActual >= resultado.totalPaginas() - 1);
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