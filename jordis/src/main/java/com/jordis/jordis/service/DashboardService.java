package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VentaRepository          ventaRepository;
    private final ProductoRepository       productoRepository;
    private final ClienteRepository        clienteRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;
    private final AlertaSistemaRepository  alertaRepository;
    private final AuditoriaLogRepository   auditoriaLogRepository;

    // ── Período dinámico ──────────────────────────────────────────────

    public LocalDateTime getDesde(String periodo) {
        return switch (periodo) {
            case "HOY"     -> LocalDate.now().atStartOfDay();
            case "SEMANA"  -> LocalDate.now().minusDays(6).atStartOfDay();
            case "MES"     -> LocalDate.now().withDayOfMonth(1).atStartOfDay();
            case "ANIO"    -> LocalDate.now().withDayOfYear(1).atStartOfDay();
            default        -> LocalDate.now().atStartOfDay();
        };
    }

    public LocalDateTime getHasta() {
        return LocalDateTime.now();
    }

    // ── KPIs principales ─────────────────────────────────────────────

    public BigDecimal getTotalVentas(LocalDateTime desde, LocalDateTime hasta) {
        return ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long getNumeroVentas(LocalDateTime desde, LocalDateTime hasta) {
        return ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada()).count();
    }

    public BigDecimal getTicketPromedio(LocalDateTime desde,
                                        LocalDateTime hasta) {
        List<Venta> ventas = ventaRepository.findEntreFechas(desde, hasta)
                .stream().filter(v -> !v.getAnulada()).toList();
        if (ventas.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = ventas.stream().map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(ventas.size()),
                2, RoundingMode.HALF_UP);
    }

    public double getVariacionVentas(LocalDateTime desde,
                                     LocalDateTime hasta) {
        Duration duracion = Duration.between(desde, hasta);
        LocalDateTime desdeAnterior = desde.minus(duracion);
        LocalDateTime hastaAnterior = desde;

        BigDecimal actual = getTotalVentas(desde, hasta);
        BigDecimal anterior = getTotalVentas(desdeAnterior, hastaAnterior);

        if (anterior.compareTo(BigDecimal.ZERO) == 0) return 0;
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public double getVariacionNumeroVentas(LocalDateTime desde,
                                           LocalDateTime hasta) {
        Duration duracion = Duration.between(desde, hasta);
        long actual   = getNumeroVentas(desde, hasta);
        long anterior = getNumeroVentas(desde.minus(duracion), desde);
        if (anterior == 0) return 0;
        return ((double)(actual - anterior) / anterior) * 100;
    }

    // ── Serie de la gráfica, correcta para cada métrica ───────────────

    public Map<String, Double> getSeriePorPeriodo(String periodo, String metrica) {
        Map<String, List<Venta>> porBucket = agruparVentasPorBucket(periodo);
        Map<String, Double> resultado = new LinkedHashMap<>();
        for (var e : porBucket.entrySet()) {
            List<Venta> ventas = e.getValue();
            double valor = switch (metrica) {
                case "CANTIDAD" -> ventas.size();
                case "TICKET" -> ventas.isEmpty() ? 0 :
                        ventas.stream().mapToDouble(v -> v.getTotal().doubleValue()).sum()
                                / ventas.size();
                case "PRODUCTOS" -> ventas.stream()
                        .flatMap(v -> v.getDetalles().stream())
                        .mapToLong(VentaProducto::getCantidad).sum();
                default -> ventas.stream()
                        .mapToDouble(v -> v.getTotal().doubleValue()).sum();
            };
            resultado.put(e.getKey(), valor);
        }
        return resultado;
    }

    private Map<String, List<Venta>> agruparVentasPorBucket(String periodo) {
        Map<String, List<Venta>> resultado = new LinkedHashMap<>();
        DateTimeFormatter fmt;

        switch (periodo) {
            case "HOY" -> {
                fmt = DateTimeFormatter.ofPattern("HH:00");
                for (int i = 11; i >= 0; i--) {
                    LocalDateTime hora = LocalDateTime.now()
                            .minusHours(i).withMinute(0).withSecond(0);
                    resultado.put(hora.format(fmt), new ArrayList<>());
                }
                ventaRepository.findActivas().stream()
                        .filter(v -> v.getFechaHora().toLocalDate()
                                .equals(LocalDate.now()))
                        .forEach(v -> {
                            String key = v.getFechaHora()
                                    .withMinute(0).withSecond(0).format(fmt);
                            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                        });
            }
            case "SEMANA" -> {
                fmt = DateTimeFormatter.ofPattern("EEE dd", new Locale("es"));
                for (int i = 6; i >= 0; i--) {
                    LocalDate dia = LocalDate.now().minusDays(i);
                    resultado.put(dia.format(fmt), new ArrayList<>());
                }
                LocalDateTime desde = LocalDate.now().minusDays(6).atStartOfDay();
                ventaRepository.findEntreFechas(desde, LocalDateTime.now())
                        .stream().filter(v -> !v.getAnulada()).forEach(v -> {
                            String key = v.getFechaHora().toLocalDate().format(fmt);
                            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                        });
            }
            case "MES" -> {
                fmt = DateTimeFormatter.ofPattern("dd/MM");
                LocalDate inicio = LocalDate.now().withDayOfMonth(1);
                LocalDate hoy    = LocalDate.now();
                LocalDate cursor = inicio;
                while (!cursor.isAfter(hoy)) {
                    resultado.put(cursor.format(fmt), new ArrayList<>());
                    cursor = cursor.plusDays(1);
                }
                ventaRepository.findEntreFechas(
                                inicio.atStartOfDay(), LocalDateTime.now())
                        .stream().filter(v -> !v.getAnulada()).forEach(v -> {
                            String key = v.getFechaHora().toLocalDate().format(fmt);
                            resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                        });
            }
            default -> {
                fmt = DateTimeFormatter.ofPattern("MMM", new Locale("es"));
                for (int i = 11; i >= 0; i--) {
                    String mes = LocalDate.now().minusMonths(i).format(fmt);
                    resultado.put(mes, new ArrayList<>());
                }
                ventaRepository.findActivas().forEach(v -> {
                    String key = v.getFechaHora().toLocalDate().format(fmt);
                    resultado.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                });
            }
        }
        return resultado;
    }

    // ── Top productos ─────────────────────────────────────────────────

    public List<Map<String, Object>> getTopProductos(
            LocalDateTime desde, LocalDateTime hasta, int limite) {
        Map<Producto, long[]> agrupado = new LinkedHashMap<>();
        ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .flatMap(v -> v.getDetalles().stream())
                .forEach(vp -> {
                    agrupado.computeIfAbsent(
                            vp.getProducto(), k -> new long[2]);
                    agrupado.get(vp.getProducto())[0] += vp.getCantidad();
                    agrupado.get(vp.getProducto())[1] +=
                            vp.getSubtotal().longValue();
                });

        return agrupado.entrySet().stream()
                .sorted((a, b) -> {
                    int porUnidades = Long.compare(b.getValue()[0], a.getValue()[0]);
                    return porUnidades != 0
                            ? porUnidades
                            : Long.compare(b.getValue()[1], a.getValue()[1]);
                })
                .limit(limite)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre",    e.getKey().getNombre());
                    m.put("unidades",  e.getValue()[0]);
                    m.put("ingresos",  BigDecimal.valueOf(e.getValue()[1]));
                    return m;
                })
                .toList();
    }

    // ── Clientes ──────────────────────────────────────────────────────

    public List<Map<String, Object>> getTopClientes(
            LocalDateTime desde, LocalDateTime hasta) {
        Map<Cliente, BigDecimal> agrupado = new LinkedHashMap<>();
        ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada() && v.getCliente() != null)
                .forEach(v -> agrupado.merge(
                        v.getCliente(), v.getTotal(), BigDecimal::add));

        return agrupado.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre", e.getKey().getNombreCompleto());
                    m.put("total",  e.getValue());
                    return m;
                })
                .toList();
    }

    public long getTotalClientes() {
        return clienteRepository.findActivos().size();
    }

    public long getClientesNuevos(LocalDateTime desde) {
        return clienteRepository.findActivos().stream()
                .filter(c -> c.getCreatedAt() != null
                        && c.getCreatedAt().isAfter(desde))
                .count();
    }

    // ── Créditos ──────────────────────────────────────────────────────

    public BigDecimal getTotalCreditosPendientes() {
        return ventaRepository.findCreditos().stream()
                .filter(v -> !v.estaCancelado())
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long getCreditosVencidos() {
        return ventaRepository.findCreditos().stream()
                .filter(v -> !v.estaCancelado()
                        && v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now()))
                .count();
    }

    public long getCreditosPorVencer() {
        LocalDateTime limite = LocalDateTime.now().plusDays(7);
        return ventaRepository.findCreditos().stream()
                .filter(v -> !v.estaCancelado()
                        && v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito().isAfter(LocalDateTime.now())
                        && v.getFechaLimiteCredito().isBefore(limite))
                .count();
    }

    // ── Inventario ────────────────────────────────────────────────────

    public long getProductosStockBajo() {
        return productoRepository.findProductosStockBajo().size();
    }

    public long getProductosSinStock() {
        return productoRepository.findByActivoTrue().stream()
                .filter(p -> p.getStock() == 0).count();
    }

    public long getTotalProductos() {
        return productoRepository.findByActivoTrue().size();
    }

    public List<Producto> getProductosCriticos() {
        return productoRepository.findProductosStockBajo().stream()
                .limit(5).toList();
    }

    // ── Cuentas por pagar ─────────────────────────────────────────────

    public BigDecimal getTotalCuentasPorPagar() {
        return cuentaPorPagarRepository.findPendientes().stream()
                .map(CuentaPorPagar::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long getCuentasVencidas() {
        return cuentaPorPagarRepository.findPendientes().stream()
                .filter(c -> c.getFechaLimite() != null
                        && c.getFechaLimite().isBefore(LocalDateTime.now()))
                .count();
    }

    // ── Alertas ───────────────────────────────────────────────────────

    public long getAlertasNoLeidas() {
        return alertaRepository.contarNoLeidas();
    }

    public List<AlertaSistema> getAlertasCriticas() {
        return alertaRepository.findNoLeidas().stream()
                .filter(a -> AlertaService.getPrioridad(a.getTipo()) <= 2)
                .sorted((a, b) -> Integer.compare(
                        AlertaService.getPrioridad(a.getTipo()),
                        AlertaService.getPrioridad(b.getTipo())))
                .limit(4).toList();
    }

    // ── Actividad reciente (toda la actividad, no solo ventas) ────────

    public List<AuditoriaLog> getActividadReciente(int limite) {
        return auditoriaLogRepository.findTodas().stream()
                .limit(limite).toList();
    }

    // ── Metas de ventas ───────────────────────────────────────────────

    public double getPorcentajeMeta(LocalDateTime desde,
                                    LocalDateTime hasta,
                                    double meta) {
        BigDecimal actual = getTotalVentas(desde, hasta);
        if (meta <= 0) return 0;
        return Math.min(actual.doubleValue() / meta * 100, 100);
    }

    // ── Métodos de ventas del dashboard original ──────────────────────

    public BigDecimal getTotalVentasHoy() {
        return getTotalVentas(
                LocalDate.now().atStartOfDay(), LocalDateTime.now());
    }

    public BigDecimal getTotalVentasMes() {
        return getTotalVentas(
                LocalDate.now().withDayOfMonth(1).atStartOfDay(),
                LocalDateTime.now());
    }

    public long getTransaccionesHoy() {
        return getNumeroVentas(
                LocalDate.now().atStartOfDay(), LocalDateTime.now());
    }
}