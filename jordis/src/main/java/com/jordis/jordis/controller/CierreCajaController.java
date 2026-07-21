package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.CierreCaja;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.service.AutenticacionService;
import com.jordis.jordis.service.CierreCajaService;
import com.jordis.jordis.controller.HistorialCierresController;
import com.jordis.jordis.service.FacturaService;
import com.jordis.jordis.util.CampoDecimalUtil;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class CierreCajaController {

    @FXML private javafx.scene.layout.VBox panelAbrirCaja;
    @FXML private TextField txtNombreCajaApertura;
    @FXML private TextField txtFondoInicialApertura;
    @FXML private Label lblMensajeApertura;

    @FXML private javafx.scene.layout.VBox panelCierre;
    @FXML private Label lblNombreCaja;
    @FXML private Label lblCajero;
    @FXML private Label lblApertura;
    @FXML private Label lblFondoInicial;

    @FXML private Label lblTotalVentas;
    @FXML private Label lblNumeroVentas;
    @FXML private Label lblTicketPromedio;
    @FXML private Label lblProductosVendidos;

    @FXML private Label lblEfectivo;
    @FXML private Label lblTarjeta;
    @FXML private Label lblTransferencia;
    @FXML private Label lblCredito;

    @FXML private TextField txtGastos;
    @FXML private TextField txtRetiros;
    @FXML private Label lblFondoInicialMov;
    @FXML private Label lblPagosCreditos;
    @FXML private Label lblPagosProveedores;
    @FXML private Label lblEfectivoEsperado;

    @FXML private TextField txtEfectivoContado;
    @FXML private Label lblDiferenciaTexto;
    @FXML private javafx.scene.layout.VBox cajaResultado;

    @FXML private TextArea txtObservacion;
    @FXML private Label lblObsRequerida;

    @FXML private Label lblCreditosCobradosHoy;
    @FXML private Label lblPagosProveedoresHoy;
    @FXML private Label lblTotalPorCobrar;
    @FXML private Label lblTotalPorPagar;

    @FXML private Button btnCerrarCaja;
    @FXML private Button btnExportarPdf;
    @FXML private Label lblMensaje;
    @FXML private Label lblEstadoCaja;

    private final CierreCajaService cierreCajaService;
    private final AutenticacionService autenticacionService;
    private final FacturaService facturaService;
    private final SpringFXMLLoader fxmlLoader;

    private CierreCaja calculoActual;
    private CierreCaja cierreGuardado;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        CampoDecimalUtil.aplicarFormatoMonetario(txtFondoInicialApertura);
        CampoDecimalUtil.aplicarFormatoMonetario(txtGastos);
        CampoDecimalUtil.aplicarFormatoMonetario(txtRetiros);
        CampoDecimalUtil.aplicarFormatoMonetario(txtEfectivoContado);

        txtEfectivoContado.textProperty().addListener((obs, old, val) -> actualizarDiferencia());
        btnExportarPdf.setDisable(true);

        refrescarEstado();
    }

    private void refrescarEstado() {
        boolean abierta = cierreCajaService.hayCajaAbierta();
        panelAbrirCaja.setVisible(!abierta);
        panelAbrirCaja.setManaged(!abierta);
        panelCierre.setVisible(abierta);
        panelCierre.setManaged(abierta);

        if (abierta) {
            recalcular();
        }
    }

    @FXML
    public void onAbrirCaja() {
        Usuario u = autenticacionService.getUsuarioActivo();
        BigDecimal fondo = CampoDecimalUtil.obtenerValor(txtFondoInicialApertura);
        String nombreCaja = txtNombreCajaApertura.getText();

        try {
            cierreCajaService.abrirCaja(u, nombreCaja, fondo);
            lblMensajeApertura.setText("");
            cierreGuardado = null;
            btnExportarPdf.setDisable(true);
            lblEstadoCaja.setVisible(false);
            txtGastos.setText("0.00");
            txtRetiros.setText("0.00");
            txtEfectivoContado.clear();
            txtObservacion.clear();
            habilitarFormulario();
            refrescarEstado();
        } catch (CierreCajaService.CajaYaAbiertaException e) {
            lblMensajeApertura.setText(e.getMessage());
        }
    }

    @FXML
    public void onRecalcular() {
        recalcular();
    }

    private void recalcular() {
        Usuario u = autenticacionService.getUsuarioActivo();
        BigDecimal gastos  = CampoDecimalUtil.obtenerValor(txtGastos);
        BigDecimal retiros = CampoDecimalUtil.obtenerValor(txtRetiros);

        try {
            calculoActual = cierreCajaService.calcular(u, gastos, retiros);
        } catch (CierreCajaService.CajaNoAbiertaException e) {
            refrescarEstado();
            return;
        }

        lblNombreCaja.setText(calculoActual.getNombreCaja());
        lblCajero.setText(u != null ? u.getNombreCompleto() : "—");
        lblApertura.setText(calculoActual.getFechaApertura().format(FMT));
        lblFondoInicial.setText("RD$" + fmt(calculoActual.getFondoInicial()));
        lblFondoInicialMov.setText("RD$" + fmt(calculoActual.getFondoInicial()));

        lblTotalVentas.setText("RD$" + fmt(calculoActual.getTotalVentas()));
        lblNumeroVentas.setText(String.valueOf(calculoActual.getNumeroVentas()));
        lblTicketPromedio.setText("RD$" + fmt(calculoActual.getTicketPromedio()));
        lblProductosVendidos.setText(String.valueOf(calculoActual.getProductosVendidos()));

        lblEfectivo.setText("RD$" + fmt(calculoActual.getMontoEfectivo()));
        lblTarjeta.setText("RD$" + fmt(calculoActual.getMontoTarjeta()));
        lblTransferencia.setText("RD$" + fmt(calculoActual.getMontoTransferencia()));
        lblCredito.setText("RD$" + fmt(calculoActual.getMontoCredito()));

        lblPagosCreditos.setText("RD$" + fmt(calculoActual.getPagosCreditos()));
        lblPagosProveedores.setText("- RD$" + fmt(calculoActual.getPagosProveedores()));
        lblEfectivoEsperado.setText("RD$" + fmt(calculoActual.getEfectivoEsperado()));

        lblCreditosCobradosHoy.setText("RD$" + fmt(calculoActual.getPagosCreditos()));
        lblPagosProveedoresHoy.setText("RD$" + fmt(calculoActual.getPagosProveedores()));
        lblTotalPorCobrar.setText("RD$" + fmt(calculoActual.getTotalPorCobrarPendiente()));
        lblTotalPorPagar.setText("RD$" + fmt(calculoActual.getTotalPorPagarPendiente()));

        actualizarDiferencia();
    }

    private void actualizarDiferencia() {
        if (calculoActual == null) return;
        BigDecimal contado = CampoDecimalUtil.obtenerValor(txtEfectivoContado);
        BigDecimal diferencia = contado.subtract(calculoActual.getEfectivoEsperado());

        String estado = diferencia.compareTo(BigDecimal.ZERO) == 0 ? "EXACTO"
                : diferencia.compareTo(BigDecimal.ZERO) > 0 ? "SOBRANTE" : "FALTANTE";

        String colorFondo, colorTexto, etiqueta;
        switch (estado) {
            case "SOBRANTE" -> { colorFondo = "#FFFBEB"; colorTexto = "#B45309"; etiqueta = "▲ SOBRANTE"; }
            case "FALTANTE" -> { colorFondo = "#FEF2F2"; colorTexto = "#DC2626"; etiqueta = "▼ FALTANTE"; }
            default          -> { colorFondo = "#F0FDF4"; colorTexto = "#15803D"; etiqueta = "✔ EXACTO"; }
        }

        cajaResultado.setStyle("-fx-background-color: " + colorFondo
                + "; -fx-background-radius: 10; -fx-padding: 16;"
                + " -fx-border-color: " + colorTexto + "; -fx-border-radius: 10;"
                + " -fx-border-width: 1.5;");
        lblDiferenciaTexto.setText(etiqueta + "   RD$" + fmt(diferencia.abs()));
        lblDiferenciaTexto.setStyle("-fx-font-size: 20; -fx-font-weight: bold;"
                + " -fx-text-fill: " + colorTexto + ";");

        boolean requiereObs = diferencia.compareTo(BigDecimal.ZERO) != 0;
        lblObsRequerida.setVisible(requiereObs);
        lblObsRequerida.setManaged(requiereObs);
    }

    @FXML
    public void onCerrarCaja() {
        if (cierreGuardado != null) {
            mostrarMensaje("Esta caja ya fue cerrada.", true);
            return;
        }
        recalcular();
        if (calculoActual == null) return;

        BigDecimal contado = CampoDecimalUtil.obtenerValor(txtEfectivoContado);
        String observacion = txtObservacion.getText() != null
                ? txtObservacion.getText().trim() : "";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar cierre de caja");
        confirm.setHeaderText("¿Cerrar la caja \"" + calculoActual.getNombreCaja() + "\"?");
        confirm.setContentText("Después de cerrar, la caja quedará cerrada, no se podrá "
                + "modificar, y se generará el reporte final.");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                cierreGuardado = cierreCajaService.confirmar(calculoActual, contado, observacion);
                bloquearFormulario();
                btnExportarPdf.setDisable(false);
                mostrarMensaje("Caja cerrada correctamente.", false);
                lblEstadoCaja.setText("🔒 CAJA CERRADA");
                lblEstadoCaja.setVisible(true);
            } catch (CierreCajaService.ObservacionRequeridaException ex) {
                mostrarMensaje(ex.getMessage(), true);
            } catch (Exception ex) {
                mostrarMensaje("Error al cerrar la caja: " + ex.getMessage(), true);
            }
        });
    }

    @FXML
    public void onExportarPdf() {
        if (cierreGuardado == null) {
            mostrarMensaje("Primero debes cerrar la caja para generar el reporte.", true);
            return;
        }
        try {
            String ruta = cierreCajaService.generarPdf(cierreGuardado);
            facturaService.abrirPDF(ruta);
        } catch (Exception e) {
            log.error("Error generando PDF de cierre de caja", e);
            mostrarMensaje("Error al generar el PDF: " + e.getMessage(), true);
        }
    }

    @FXML
    public void onVerHistorial() {
        try {
            SpringFXMLLoader.LoadResult<HistorialCierresController> result =
                    fxmlLoader.loadWithController("/fxml/historial_cierres.fxml");
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Historial de Cierres de Caja");
            stage.setScene(new Scene(result.root, 1000, 620));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo historial de cierres", e);
            mostrarMensaje("Error al abrir el historial: " + e.getMessage(), true);
        }
    }

    private void bloquearFormulario() {
        txtGastos.setDisable(true);
        txtRetiros.setDisable(true);
        txtEfectivoContado.setDisable(true);
        txtObservacion.setDisable(true);
        btnCerrarCaja.setDisable(true);
    }

    private void habilitarFormulario() {
        txtGastos.setDisable(false);
        txtRetiros.setDisable(false);
        txtEfectivoContado.setDisable(false);
        txtObservacion.setDisable(false);
        btnCerrarCaja.setDisable(false);
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 8 0; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}