package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertaService {

    private final AlertaSistemaRepository  alertaRepository;
    private final ProductoRepository       productoRepository;
    private final UsuarioRepository        usuarioRepository;
    private final VentaRepository          ventaRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    // ── Prioridades ───────────────────────────────────────────────────
    // CRITICA = 1, ALTA = 2, MEDIA = 3, BAJA = 4, LEIDA_SIN_RESOLVER = 5
    public static int getPrioridad(String tipo) {
        return switch (tipo) {
            case "SIN_STOCK",
                 "CREDITO_VENCIMIENTO",
                 "CUENTA_POR_PAGAR"          -> 1; // Crítica
            case "STOCK_BAJO",
                 "PRECIO_FUERA_RANGO_ALTA"   -> 2; // Alta
            case "PRECIO_FUERA_RANGO_MEDIA"  -> 3; // Media
            case "USUARIO_BLOQUEADO",
                 "PROXIMO_MINIMO"            -> 4; // Baja
            default                          -> 4; // Baja
        };
    }

    // Prioridad "efectiva" de una alerta puntual: si ya fue marcada como
    // leída (pero sigue sin resolverse porque la condición sigue activa),
    // siempre cae al nivel 5 — por debajo de Baja — sin importar su tipo.
    public static int getPrioridadEfectiva(AlertaSistema alerta) {
        if (Boolean.TRUE.equals(alerta.getLeida())) return 5;
        return getPrioridad(alerta.getTipo());
    }

    public static String getNombrePrioridad(int prioridad) {
        return switch (prioridad) {
            case 1 -> "Crítica";
            case 2 -> "Alta";
            case 3 -> "Media";
            case 4 -> "Baja";
            case 5 -> "Leída — sin resolver";
            default -> "Baja";
        };
    }

    public static String getColorPrioridad(int prioridad) {
        return switch (prioridad) {
            case 1 -> "#DC2626";
            case 2 -> "#EA580C";
            case 3 -> "#B45309";
            case 4 -> "#2563EB";
            case 5 -> "#64748B";
            default -> "#2563EB";
        };
    }

    // ── Consultas ─────────────────────────────────────────────────────

    // Devuelve TODAS las alertas vigentes (leídas-sin-resolver incluidas;
    // las resueltas se borran físicamente por auto-limpieza, así que todo
    // lo que queda en la tabla sigue siendo relevante de una forma u otra).
    // Orden: primero por prioridad efectiva (las leídas-sin-resolver
    // siempre al final) y luego por fecha, más recientes primero.
    public List<AlertaSistema> obtenerActivas() {
        return alertaRepository.findTodas().stream()
                .sorted((a, b) -> {
                    int pa = getPrioridadEfectiva(a);
                    int pb = getPrioridadEfectiva(b);
                    if (pa != pb) return Integer.compare(pa, pb);
                    return b.getFechaHora().compareTo(a.getFechaHora());
                })
                .toList();
    }

    // Solo las realmente no leídas (para el contador del menú lateral)
    public long contarNoLeidas() {
        return alertaRepository.contarNoLeidas();
    }

    // prioridad 1-4: cuenta solo alertas NO leídas de esa severidad.
    // prioridad 5: cuenta las leídas-sin-resolver (de cualquier severidad).
    public long contarPorPrioridad(int prioridad) {
        if (prioridad == 5) {
            return alertaRepository.findTodas().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getLeida()))
                    .count();
        }
        return alertaRepository.findTodas().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getLeida()))
                .filter(a -> getPrioridad(a.getTipo()) == prioridad)
                .count();
    }

    // Marca la alerta como leída, pero sigue existiendo como
    // "leída — sin resolver" hasta que la condición que la generó
    // desaparezca (entonces la auto-limpieza la borra) o se reabra.
    @Transactional
    public void marcarLeida(Integer idAlerta) {
        alertaRepository.findById(idAlerta).ifPresent(a -> {
            a.setLeida(true);
            alertaRepository.save(a);
        });
    }

    // Vuelve a poner una alerta "leída — sin resolver" como activa
    @Transactional
    public void reabrir(Integer idAlerta) {
        alertaRepository.findById(idAlerta).ifPresent(a -> {
            a.setLeida(false);
            alertaRepository.save(a);
        });
    }

    @Transactional
    public void eliminar(Integer idAlerta) {
        alertaRepository.deleteById(idAlerta);
    }

    // Alerta de usuario bloqueado — siempre crea una nueva
    @Transactional
    public void alertaUsuarioBloqueado(Usuario usuario) {
        AlertaSistema alerta = new AlertaSistema();
        alerta.setTipo("USUARIO_BLOQUEADO");
        alerta.setTitulo("Usuario bloqueado: " + usuario.getNombreCompleto());
        alerta.setDescripcion("El usuario '" + usuario.getNombre()
                + "' fue bloqueado por múltiples intentos fallidos.");
        alerta.setIdReferencia(usuario.getIdUsuario());
        alertaRepository.save(alerta);
    }

    // ── Escaneo completo con auto-limpieza ───────────────────────────

    @Transactional
    public void escanearTodo() {
        limpiarTiposLegado();
        escanearStockBajo();
        escanearDiferenciaPrecios();
        limpiarAlertasUsuariosBloqueados();
        escanearCreditosPorVencer();
        escanearCuentasPorPagar();
    }

    // Tipos de alerta que ya no existen (de versiones anteriores del
    // sistema). Cualquier fila vieja con estos tipos se elimina para que
    // no quede huérfana mostrándose con una prioridad/etiqueta incorrecta.
    private static final List<String> TIPOS_LEGADO =
            List.of("PRECIO_DIFERENCIA", "PRECIO_COMPRA_INUSUAL");

    @Transactional
    public void limpiarTiposLegado() {
        alertaRepository.findTodas().stream()
                .filter(a -> TIPOS_LEGADO.contains(a.getTipo()))
                .forEach(alertaRepository::delete);
    }

    // Tipos de alerta relacionados a niveles de stock, en orden de gravedad
    private static final List<String> TIPOS_STOCK =
            List.of("SIN_STOCK", "STOCK_BAJO", "PROXIMO_MINIMO");

    @Transactional
    public void escanearStockBajo() {
        List<Producto> activos = productoRepository.findByActivoTrue();
        for (Producto p : activos) {
            String tipoActual = determinarTipoStock(p);

            for (String tipo : TIPOS_STOCK) {
                // Se busca en TODAS las alertas (leídas o no) para no
                // recrear como "nueva" una que el usuario ya marcó leída.
                Optional<AlertaSistema> existente = alertaRepository.findTodas()
                        .stream()
                        .filter(a -> tipo.equals(a.getTipo())
                                && p.getIdProducto().equals(a.getIdReferencia()))
                        .findFirst();

                if (tipo.equals(tipoActual)) {
                    String titulo = tituloStock(tipo, p);
                    String desc   = descripcionStock(tipo, p);
                    if (existente.isPresent()) {
                        // Ya existía (leída o no): solo se actualiza el
                        // contenido, sin tocar el estado "leída".
                        AlertaSistema a = existente.get();
                        a.setTitulo(titulo);
                        a.setDescripcion(desc);
                        alertaRepository.save(a);
                    } else {
                        AlertaSistema a = new AlertaSistema();
                        a.setTipo(tipo);
                        a.setTitulo(titulo);
                        a.setDescripcion(desc);
                        a.setIdReferencia(p.getIdProducto());
                        alertaRepository.save(a);
                    }
                } else {
                    // ← Auto-limpieza: este nivel ya no aplica al producto
                    // (se resolvió, así que se borra sin importar si estaba leída)
                    existente.ifPresent(alertaRepository::delete);
                }
            }
        }
    }

    // Determina el nivel de alerta de stock que le corresponde a un producto,
    // o null si el stock está en un nivel saludable.
    private String determinarTipoStock(Producto p) {
        int stock  = p.getStock();
        int minimo = p.getStockMinimo();
        if (stock <= 0) return "SIN_STOCK";
        if (stock <= minimo) return "STOCK_BAJO";
        // "Próximo al mínimo": todavía por encima del mínimo, pero cerca
        // (dentro de un 50% por encima del stock mínimo configurado)
        double umbralProximo = minimo * 1.5;
        if (stock <= Math.ceil(umbralProximo)) return "PROXIMO_MINIMO";
        return null;
    }

    private String tituloStock(String tipo, Producto p) {
        return switch (tipo) {
            case "SIN_STOCK"      -> "Sin stock: " + p.getNombre();
            case "STOCK_BAJO"     -> "Stock bajo: " + p.getNombre();
            case "PROXIMO_MINIMO" -> "Próximo al mínimo: " + p.getNombre();
            default                -> p.getNombre();
        };
    }

    private String descripcionStock(String tipo, Producto p) {
        return switch (tipo) {
            case "SIN_STOCK" -> "'" + p.getNombre()
                    + "' no tiene unidades disponibles (mínimo: "
                    + p.getStockMinimo() + ").";
            case "STOCK_BAJO" -> "'" + p.getNombre() + "' tiene "
                    + p.getStock() + " u. (mínimo: "
                    + p.getStockMinimo() + ").";
            case "PROXIMO_MINIMO" -> "'" + p.getNombre() + "' tiene "
                    + p.getStock() + " u., acercándose al mínimo ("
                    + p.getStockMinimo() + ").";
            default -> "";
        };
    }

    // Tipos de alerta de precio fuera de rango, en orden de gravedad
    private static final List<String> TIPOS_PRECIO =
            List.of("PRECIO_FUERA_RANGO_ALTA", "PRECIO_FUERA_RANGO_MEDIA");

    // Umbrales de diferencia porcentual entre precio de venta y sugerido
    private static final BigDecimal UMBRAL_PRECIO_MEDIA = new BigDecimal("20");
    private static final BigDecimal UMBRAL_PRECIO_ALTA  = new BigDecimal("35");

    @Transactional
    public void escanearDiferenciaPrecios() {
        for (Producto p : productoRepository.findByActivoTrue()) {
            String tipoActual = null;
            BigDecimal dif = null;

            if (p.getPrecioUnitario() != null && p.getPrecioSugerido() != null
                    && p.getPrecioSugerido().compareTo(BigDecimal.ZERO) != 0) {

                dif = p.getPrecioUnitario()
                        .subtract(p.getPrecioSugerido()).abs()
                        .divide(p.getPrecioSugerido(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                if (dif.compareTo(UMBRAL_PRECIO_ALTA) >= 0) {
                    tipoActual = "PRECIO_FUERA_RANGO_ALTA";
                } else if (dif.compareTo(UMBRAL_PRECIO_MEDIA) >= 0) {
                    tipoActual = "PRECIO_FUERA_RANGO_MEDIA";
                }
            }

            for (String tipo : TIPOS_PRECIO) {
                Optional<AlertaSistema> existente = alertaRepository.findTodas()
                        .stream()
                        .filter(a -> tipo.equals(a.getTipo())
                                && p.getIdProducto().equals(a.getIdReferencia()))
                        .findFirst();

                if (tipo.equals(tipoActual)) {
                    String desc = String.format(
                            "Precio venta RD$%s difiere %.1f%% del sugerido RD$%s.",
                            p.getPrecioUnitario().toPlainString(),
                            dif.floatValue(),
                            p.getPrecioSugerido().toPlainString());
                    if (existente.isPresent()) {
                        AlertaSistema a = existente.get();
                        a.setDescripcion(desc);
                        alertaRepository.save(a);
                    } else {
                        AlertaSistema a = new AlertaSistema();
                        a.setTipo(tipo);
                        a.setTitulo("Revisar precio: " + p.getNombre());
                        a.setDescripcion(desc);
                        a.setIdReferencia(p.getIdProducto());
                        alertaRepository.save(a);
                    }
                } else {
                    // ← Auto-limpieza: ya no aplica este nivel
                    existente.ifPresent(alertaRepository::delete);
                }
            }
        }
    }

    @Transactional
    public void limpiarAlertasUsuariosBloqueados() {
        alertaRepository.findTodas().stream()
                .filter(a -> "USUARIO_BLOQUEADO".equals(a.getTipo()))
                .forEach(a -> {
                    if (a.getIdReferencia() == null) return;
                    usuarioRepository.findById(a.getIdReferencia())
                            .ifPresent(u -> {
                                if (!u.getBloqueado()) {
                                    // ← Auto-limpieza: usuario desbloqueado
                                    alertaRepository.delete(a);
                                }
                            });
                });
    }

    @Transactional
    public void escanearCreditosPorVencer() {
        LocalDateTime ahora   = LocalDateTime.now();
        LocalDateTime aviso   = ahora.plusDays(7);

        ventaRepository.findActivas().stream()
                .filter(Venta::getEsCredito)
                .filter(v -> v.getFechaLimiteCredito() != null)
                .forEach(v -> {
                    Optional<AlertaSistema> existente =
                            alertaRepository.findTodas().stream()
                                    .filter(a -> "CREDITO_VENCIMIENTO".equals(a.getTipo())
                                            && v.getIdVenta().equals(a.getIdReferencia()))
                                    .findFirst();

                    if (v.estaCancelado()) {
                        // ← Auto-limpieza: crédito pagado
                        existente.ifPresent(alertaRepository::delete);
                        return;
                    }

                    boolean vencido   = v.getFechaLimiteCredito().isBefore(ahora);
                    boolean porVencer = !vencido
                            && v.getFechaLimiteCredito().isBefore(aviso);

                    if (!vencido && !porVencer) {
                        existente.ifPresent(alertaRepository::delete);
                        return;
                    }

                    String titulo = vencido
                            ? "Crédito VENCIDO: "
                            : "Crédito por vencer: ";
                    titulo += v.getCliente() != null
                            ? v.getCliente().getNombreCompleto() : "—";
                    String desc = String.format(
                            "Factura %s — Saldo: RD$%s — %s: %s",
                            v.getNumeroFactura() != null
                                    ? v.getNumeroFactura() : "#" + v.getIdVenta(),
                            v.getSaldoPendiente().toPlainString(),
                            vencido ? "Venció el" : "Vence el",
                            v.getFechaLimiteCredito().toLocalDate());

                    if (existente.isPresent()) {
                        AlertaSistema a = existente.get();
                        a.setTitulo(titulo);
                        a.setDescripcion(desc);
                        alertaRepository.save(a);
                    } else {
                        AlertaSistema a = new AlertaSistema();
                        a.setTipo("CREDITO_VENCIMIENTO");
                        a.setTitulo(titulo);
                        a.setDescripcion(desc);
                        a.setIdReferencia(v.getIdVenta());
                        alertaRepository.save(a);
                    }
                });
    }

    @Transactional
    public void escanearCuentasPorPagar() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime aviso = ahora.plusDays(7);

        cuentaPorPagarRepository.findTodas().forEach(c -> {
            Optional<AlertaSistema> existente =
                    alertaRepository.findTodas().stream()
                            .filter(a -> "CUENTA_POR_PAGAR".equals(a.getTipo())
                                    && c.getIdCuenta().equals(a.getIdReferencia()))
                            .findFirst();

            if (c.estaCancelada()) {
                // ← Auto-limpieza: cuenta pagada
                existente.ifPresent(alertaRepository::delete);
                return;
            }

            if (c.getFechaLimite() == null) return;

            boolean vencida   = c.getFechaLimite().isBefore(ahora);
            boolean porVencer = !vencida
                    && c.getFechaLimite().isBefore(aviso);

            if (!vencida && !porVencer) {
                existente.ifPresent(alertaRepository::delete);
                return;
            }

            String titulo  = (vencida ? "Cuenta VENCIDA: " : "Cuenta por vencer: ")
                    + c.getProveedor().getNombre();
            String desc    = String.format(
                    "Compra #%d — Saldo: RD$%s — %s: %s",
                    c.getCompra().getIdCompra(),
                    c.getSaldoPendiente().toPlainString(),
                    vencida ? "Venció el" : "Vence el",
                    c.getFechaLimite().toLocalDate());

            if (existente.isPresent()) {
                AlertaSistema a = existente.get();
                a.setTitulo(titulo);
                a.setDescripcion(desc);
                alertaRepository.save(a);
            } else {
                AlertaSistema a = new AlertaSistema();
                a.setTipo("CUENTA_POR_PAGAR");
                a.setTitulo(titulo);
                a.setDescripcion(desc);
                a.setIdReferencia(c.getIdCuenta());
                alertaRepository.save(a);
            }
        });
    }

    public List<AlertaSistema> getAlertasCriticas() {
        return alertaRepository.findNoLeidas().stream()
                .filter(a -> getPrioridad(a.getTipo()) <= 2)
                .limit(4).toList();
    }
}