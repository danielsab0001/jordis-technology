package com.jordis.jordis.controller;

import com.jordis.jordis.config.SpringFXMLLoader;
import com.jordis.jordis.model.AlertaSistema;
import com.jordis.jordis.service.AlertaService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertaController {

    @FXML private Label  lblSubtitulo;
    @FXML private Label  lblCritica;
    @FXML private Label  lblAlta;
    @FXML private Label  lblMedia;
    @FXML private Label  lblBaja;
    @FXML private Label  lblLeidas;
    @FXML private Label  lblTodas;
    @FXML private VBox   cardCritica;
    @FXML private VBox   cardAlta;
    @FXML private VBox   cardMedia;
    @FXML private VBox   cardBaja;
    @FXML private VBox   cardLeidas;
    @FXML private VBox   cardTodas;
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<String> cmbTipo;
    @FXML private Label  lblFiltroActivo;
    @FXML private VBox   panelGrupos;
    @FXML private VBox   panelVacio;
    @FXML private Label  lblMensaje;

    private final AlertaService    alertaService;
    private final SpringFXMLLoader fxmlLoader;
    private MainController mainController;

    // Estado de filtros
    private Integer filtroPrioridad = null;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    @FXML
    public void initialize() {
        configurarTiposCombo();
        configurarTarjetas();

        txtBuscar.textProperty().addListener(
                (obs, old, val) -> renderizarAlertas());
        cmbTipo.setOnAction(e -> renderizarAlertas());

        cargarAlertas();
    }

    private void configurarTiposCombo() {
        cmbTipo.getItems().addAll(
                null,
                "SIN_STOCK",
                "STOCK_BAJO",
                "PROXIMO_MINIMO",
                "PRECIO_FUERA_RANGO_ALTA",
                "PRECIO_FUERA_RANGO_MEDIA",
                "USUARIO_BLOQUEADO",
                "CREDITO_VENCIMIENTO",
                "CUENTA_POR_PAGAR");
        cmbTipo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String t) {
                if (t == null) return "Todos los tipos";
                return etiquetaTipo(t);
            }
            @Override public String fromString(String s) { return null; }
        });
    }

    private void configurarTarjetas() {
        Map<VBox, Integer> tarjetas = Map.of(
                cardCritica, 1, cardAlta, 2,
                cardMedia,   3, cardBaja, 4,
                cardLeidas,  5, cardTodas, 0);

        tarjetas.forEach((card, prioridad) -> {
            card.setOnMouseClicked(e -> {
                filtroPrioridad = prioridad == 0 ? null : prioridad;
                renderizarAlertas();

                // Resaltar tarjeta activa
                tarjetas.keySet().forEach(c ->
                        c.setStyle(c.getStyle()
                                .replace("; -fx-background-color: #EFF6FF;", "")
                                .replace("; -fx-background-color: #F0FDF4;",  "")));

                if (prioridad > 0) {
                    card.setStyle(card.getStyle()
                            + " -fx-background-color: #EFF6FF;");
                }
            });

            card.setOnMouseEntered(e ->
                    card.setOpacity(0.85));
            card.setOnMouseExited(e ->
                    card.setOpacity(1.0));
        });
    }

    private void cargarAlertas() {
        // Auto-escanear antes de mostrar
        alertaService.escanearTodo();
        actualizarContadores();
        renderizarAlertas();
    }

    private void actualizarContadores() {
        long critica = alertaService.contarPorPrioridad(1);
        long alta    = alertaService.contarPorPrioridad(2);
        long media   = alertaService.contarPorPrioridad(3);
        long baja    = alertaService.contarPorPrioridad(4);
        long leidas  = alertaService.contarPorPrioridad(5);
        long total   = critica + alta + media + baja + leidas;

        lblCritica.setText(String.valueOf(critica));
        lblAlta.setText(String.valueOf(alta));
        lblMedia.setText(String.valueOf(media));
        lblBaja.setText(String.valueOf(baja));
        lblLeidas.setText(String.valueOf(leidas));
        lblTodas.setText(String.valueOf(total));

        long pendientes = critica + alta + media + baja;
        lblSubtitulo.setText(pendientes > 0
                ? pendientes + " situaciones activas que requieren atención"
                : "Sin alertas activas — todo bajo control");
    }

    private void renderizarAlertas() {
        panelGrupos.getChildren().clear();
        String busqueda = txtBuscar.getText().trim().toLowerCase();
        String tipo     = cmbTipo.getValue();

        List<AlertaSistema> alertas = alertaService.obtenerActivas().stream()
                .filter(a -> filtroPrioridad == null
                        || AlertaService.getPrioridadEfectiva(a)
                        == filtroPrioridad)
                .filter(a -> tipo == null || tipo.equals(a.getTipo()))
                .filter(a -> busqueda.isEmpty()
                        || a.getTitulo().toLowerCase().contains(busqueda)
                        || (a.getDescripcion() != null
                        && a.getDescripcion().toLowerCase().contains(busqueda)))
                .toList();

        // Actualizar etiqueta de filtro activo
        if (filtroPrioridad != null || tipo != null) {
            String label = "";
            if (filtroPrioridad != null)
                label += "Prioridad: "
                        + AlertaService.getNombrePrioridad(filtroPrioridad);
            if (tipo != null)
                label += (label.isEmpty() ? "" : "  ·  ")
                        + etiquetaTipo(tipo);
            lblFiltroActivo.setText(label);
            lblFiltroActivo.setVisible(true);
        } else {
            lblFiltroActivo.setVisible(false);
        }

        if (alertas.isEmpty()) {
            panelVacio.setVisible(true);
            panelVacio.setManaged(true);
        } else {
            panelVacio.setVisible(false);
            panelVacio.setManaged(false);

            // Agrupar por tipo
            Map<String, List<AlertaSistema>> grupos = alertas.stream()
                    .collect(Collectors.groupingBy(
                            AlertaSistema::getTipo, LinkedHashMap::new,
                            Collectors.toList()));

            grupos.forEach((tipoGrupo, lista) ->
                    panelGrupos.getChildren().add(
                            crearGrupo(tipoGrupo, lista)));
        }

        lblMensaje.setText("");
    }

    // ── Construcción de grupos ────────────────────────────────────────

    private VBox crearGrupo(String tipo, List<AlertaSistema> alertas) {
        // Si TODAS las alertas del grupo ya fueron marcadas como leídas
        // (sin resolver), el grupo entero se muestra en gris neutro,
        // por debajo visualmente de cualquier severidad activa.
        boolean soloLeidas = alertas.stream()
                .allMatch(a -> Boolean.TRUE.equals(a.getLeida()));
        int prioridad = soloLeidas ? 5 : AlertaService.getPrioridad(tipo);
        String[] colores = coloresPrioridad(prioridad);

        VBox grupo = new VBox(0);
        grupo.setStyle("-fx-background-color: white; -fx-border-radius: 10;"
                + " -fx-background-radius: 10;"
                + " -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,2);");

        // Encabezado del grupo — siempre visible.
        // Fondo sólido con el color de la prioridad para que transmita
        // urgencia de un vistazo (crítica = rojo, alta = naranja, etc.)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 12 16; -fx-background-radius: 10 10 0 0;"
                + " -fx-cursor: hand; -fx-background-color: " + colores[0] + ";");

        Label lIcono = new Label(iconoPrioridad(prioridad));
        lIcono.setStyle("-fx-font-size: 14;");

        VBox infoGrupo = new VBox(2);
        Label lTipo = new Label(etiquetaTipo(tipo));
        lTipo.setStyle("-fx-font-size: 13; -fx-font-weight: bold;"
                + " -fx-text-fill: white;");
        Label lCount = new Label(soloLeidas
                ? (alertas.size() == 1 ? "1 alerta leída" : alertas.size() + " alertas leídas")
                : (alertas.size() == 1 ? "1 alerta activa" : alertas.size() + " alertas activas"));
        lCount.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.85);");
        infoGrupo.getChildren().addAll(lTipo, lCount);
        HBox.setHgrow(infoGrupo, Priority.ALWAYS);

        // Badge de prioridad — chip translúcido sobre el fondo de color
        Label lPrioridad = new Label(
                AlertaService.getNombrePrioridad(prioridad));
        lPrioridad.setStyle("-fx-background-color: rgba(255,255,255,0.22);"
                + " -fx-text-fill: white; -fx-padding: 2 8;"
                + " -fx-background-radius: 10;"
                + " -fx-font-size: 10; -fx-font-weight: bold;");

        // Flecha de expansión
        Label lFlecha = new Label("▼");
        lFlecha.setStyle("-fx-font-size: 11; -fx-text-fill: rgba(255,255,255,0.85);");

        header.getChildren().addAll(
                lIcono, infoGrupo, lPrioridad, lFlecha);

        // Contenedor expandible con las alertas individuales
        VBox contenido = new VBox(0);
        alertas.forEach(a ->
                contenido.getChildren().add(crearFilaAlerta(a)));

        // Si hay más de 3, inicialmente colapsado
        boolean expandido = alertas.size() <= 3;
        contenido.setVisible(expandido);
        contenido.setManaged(expandido);
        lFlecha.setText(expandido ? "▲" : "▼");

        header.setOnMouseClicked(e -> {
            boolean nuevoEstado = !contenido.isVisible();
            contenido.setVisible(nuevoEstado);
            contenido.setManaged(nuevoEstado);
            lFlecha.setText(nuevoEstado ? "▲" : "▼");
        });

        header.setOnMouseEntered(e -> header.setOpacity(0.9));
        header.setOnMouseExited(e -> header.setOpacity(1.0));
        grupo.getChildren().addAll(header, contenido);
        return grupo;
    }

    private HBox crearFilaAlerta(AlertaSistema alerta) {
        int prioridad = AlertaService.getPrioridadEfectiva(alerta);
        String[] colores = coloresPrioridad(prioridad);
        boolean leida = Boolean.TRUE.equals(alerta.getLeida());

        HBox fila = new HBox(12);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding: 10 16 10 20; -fx-border-color: #F1F5F9;"
                + " -fx-border-width: 0 0 1 0;"
                + (leida ? " -fx-opacity: 0.75;" : ""));

        VBox info = new VBox(3);
        Label lTitulo = new Label(alerta.getTitulo());
        lTitulo.setStyle("-fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-text-fill: #0F172A;");
        info.getChildren().add(lTitulo);

        if (leida) {
            Label lTag = new Label("Leída — sin resolver");
            lTag.setStyle("-fx-font-size: 9; -fx-font-weight: bold;"
                    + " -fx-text-fill: #64748B; -fx-background-color: #F1F5F9;"
                    + " -fx-padding: 1 6; -fx-background-radius: 4;");
            info.getChildren().add(lTag);
        }

        if (alerta.getDescripcion() != null
                && !alerta.getDescripcion().isBlank()) {
            Label lDesc = new Label(alerta.getDescripcion());
            lDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #64748B;");
            lDesc.setWrapText(true);
            info.getChildren().add(lDesc);
        }

        // Tiempo transcurrido
        HBox tiempoBox = new HBox(8);
        tiempoBox.setAlignment(Pos.CENTER_LEFT);
        Label lFecha = new Label(alerta.getFechaHora().format(FMT));
        lFecha.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
        Label lTiempo = new Label(tiempoTranscurrido(alerta.getFechaHora()));
        lTiempo.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;"
                + " -fx-background-color: #F1F5F9; -fx-background-radius: 4;"
                + " -fx-padding: 1 5;");
        tiempoBox.getChildren().addAll(lFecha, lTiempo);
        info.getChildren().add(tiempoBox);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Botones de acción
        HBox acciones = new HBox(6);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getChildren().addAll(
                crearAccionContextual(alerta),
                crearBtnLeida(alerta));

        fila.getChildren().addAll(info, acciones);
        hover(fila, "white", "#FAFAFA");
        return fila;
    }

    // ── Acción contextual según tipo ──────────────────────────────────

    private Button crearAccionContextual(AlertaSistema alerta) {
        String[] config = switch (alerta.getTipo()) {
            case "SIN_STOCK", "STOCK_BAJO", "PROXIMO_MINIMO" ->
                    new String[]{"📦  Ver inventario",  "#EFF6FF", "#2563EB"};
            case "PRECIO_FUERA_RANGO_ALTA", "PRECIO_FUERA_RANGO_MEDIA" ->
                    new String[]{"✏  Ajustar precio",   "#FEF3C7", "#B45309"};
            case "USUARIO_BLOQUEADO"     ->
                    new String[]{"👤  Ver usuario",      "#FEF2F2", "#DC2626"};
            case "CREDITO_VENCIMIENTO"   ->
                    new String[]{"📋  Ver crédito",      "#FEF3C7", "#B45309"};
            case "CUENTA_POR_PAGAR"      ->
                    new String[]{"💳  Ver cuenta",       "#FEF2F2", "#DC2626"};
            default ->
                    new String[]{"Ver",                  "#EFF6FF", "#2563EB"};
        };

        Button btn = new Button(config[0]);
        btn.setStyle("-fx-background-color: " + config[1]
                + "; -fx-text-fill: " + config[2]
                + "; -fx-border-color: " + config[2]
                + "; -fx-border-radius: 5; -fx-background-radius: 5;"
                + " -fx-font-size: 11; -fx-padding: 4 10; -fx-cursor: hand;");

        btn.setOnAction(e -> navegarAccion(alerta));
        return btn;
    }

    private Button crearBtnLeida(AlertaSistema alerta) {
        boolean leida = Boolean.TRUE.equals(alerta.getLeida());
        Button btn = new Button(leida ? "↺" : "✓");
        btn.setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #64748B;"
                + " -fx-border-color: #E2E8F0; -fx-border-radius: 5;"
                + " -fx-background-radius: 5; -fx-font-size: 11;"
                + " -fx-padding: 4 8; -fx-cursor: hand;");
        btn.setTooltip(new Tooltip(leida
                ? "Reabrir (volver a mostrarla como activa)"
                : "Marcar como leída — sin resolver"));
        btn.setOnAction(e -> {
            if (leida) {
                alertaService.reabrir(alerta.getIdAlerta());
                mostrarMensaje("Alerta reabierta.", false);
            } else {
                alertaService.marcarLeida(alerta.getIdAlerta());
                mostrarMensaje("Alerta marcada como leída — sin resolver.", false);
            }
            actualizarContadores();
            renderizarAlertas();
        });
        return btn;
    }

    private void navegarAccion(AlertaSistema alerta) {
        if (mainController == null) return;
        Integer ref = alerta.getIdReferencia();
        switch (alerta.getTipo()) {
            case "SIN_STOCK", "STOCK_BAJO", "PROXIMO_MINIMO",
                 "PRECIO_FUERA_RANGO_ALTA", "PRECIO_FUERA_RANGO_MEDIA"
                    -> mainController.onInventarioFiltrado(ref);
            case "USUARIO_BLOQUEADO"    -> mainController.onUsuariosFiltrado(ref);
            case "CREDITO_VENCIMIENTO"  -> mainController.onCreditosFiltrado(ref);
            case "CUENTA_POR_PAGAR"     -> mainController.onCuentasPorPagarFiltrado(ref);
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────

    @FXML
    public void onEscanear() {
        alertaService.escanearTodo();
        actualizarContadores();
        renderizarAlertas();
        mostrarMensaje("Actualización completada.", false);
    }

    @FXML
    public void onMarcarTodasLeidas() {
        long noLeidas = alertaService.contarNoLeidas();
        if (noLeidas == 0) {
            mostrarMensaje("No hay alertas pendientes.", false);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Marcar " + noLeidas + " alerta(s) como leídas?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                alertaService.obtenerActivas().stream()
                        .filter(a -> !Boolean.TRUE.equals(a.getLeida()))
                        .forEach(a -> alertaService.marcarLeida(a.getIdAlerta()));
                actualizarContadores();
                renderizarAlertas();
                mostrarMensaje("Alertas marcadas como leídas — sin resolver.", false);
            }
        });
    }

    @FXML
    public void onLimpiarFiltros() {
        filtroPrioridad = null;
        txtBuscar.clear();
        cmbTipo.setValue(null);
        lblFiltroActivo.setVisible(false);
        renderizarAlertas();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String tiempoTranscurrido(LocalDateTime desde) {
        Duration d = Duration.between(desde, LocalDateTime.now());
        if (d.toMinutes() < 1)   return "Hace un momento";
        if (d.toMinutes() < 60)  return "Hace " + d.toMinutes() + " min.";
        if (d.toHours() < 24)    return "Hace " + d.toHours() + " h.";
        if (d.toDays() < 7)      return "Hace " + d.toDays() + " día(s)";
        return "Hace " + (d.toDays() / 7) + " semana(s)";
    }

    private String etiquetaTipo(String tipo) {
        return switch (tipo) {
            case "SIN_STOCK"                -> "Sin stock";
            case "STOCK_BAJO"               -> "Stock bajo";
            case "PROXIMO_MINIMO"           -> "Próximo al mínimo";
            case "PRECIO_FUERA_RANGO_ALTA"  -> "Precio fuera de rango (alta)";
            case "PRECIO_FUERA_RANGO_MEDIA" -> "Precio fuera de rango (media)";
            case "USUARIO_BLOQUEADO"        -> "Usuario bloqueado";
            case "CREDITO_VENCIMIENTO"      -> "Crédito por vencer";
            case "CUENTA_POR_PAGAR"         -> "Cuenta por pagar";
            default                         -> tipo;
        };
    }

    private String iconoPrioridad(int prioridad) {
        return switch (prioridad) {
            case 1 -> "🔴";
            case 2 -> "🟠";
            case 3 -> "🟡";
            case 4 -> "🔵";
            default -> "⚪"; // 5: leída — sin resolver
        };
    }

    private String[] coloresPrioridad(int prioridad) {
        return switch (prioridad) {
            case 1 -> new String[]{"#DC2626", "#FEF2F2"};
            case 2 -> new String[]{"#EA580C", "#FFF7ED"};
            case 3 -> new String[]{"#B45309", "#FEF3C7"};
            case 4 -> new String[]{"#2563EB", "#EFF6FF"};
            default-> new String[]{"#64748B", "#F1F5F9"}; // 5
        };
    }

    private void hover(Region node, String normal, String over) {
        String base = node.getStyle();
        node.setOnMouseEntered(e ->
                node.setStyle(base + " -fx-background-color: " + over + ";"));
        node.setOnMouseExited(e ->
                node.setStyle(base));
    }

    private void mostrarMensaje(String texto, boolean esError) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle("-fx-padding: 8 24; -fx-font-size: 12; -fx-text-fill: "
                + (esError ? "#DC2626" : "#16A34A") + ";");
    }
}