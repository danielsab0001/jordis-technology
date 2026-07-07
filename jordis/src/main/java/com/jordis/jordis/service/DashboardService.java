package com.jordis.jordis.service;

import com.jordis.jordis.model.Venta;
import com.jordis.jordis.repository.AlertaSistemaRepository;
import com.jordis.jordis.repository.CuentaPorPagarRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;
    private final AlertaSistemaRepository alertaRepository;

    public BigDecimal getTotalVentasHoy() {

        LocalDate hoy = LocalDate.now();

        BigDecimal total = ventaRepository.totalVentas(
                hoy.atStartOfDay(),
                hoy.plusDays(1).atStartOfDay()
        );

        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotalVentasAyer() {

        LocalDate ayer = LocalDate.now().minusDays(1);

        BigDecimal total = ventaRepository.totalVentas(
                ayer.atStartOfDay(),
                ayer.plusDays(1).atStartOfDay()
        );

        return total != null ? total : BigDecimal.ZERO;
    }

    public long getTransaccionesHoy() {

        LocalDate hoy = LocalDate.now();

        return ventaRepository.countVentas(
                hoy.atStartOfDay(),
                hoy.plusDays(1).atStartOfDay()
        );
    }

    public BigDecimal getTotalVentasMes() {

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);

        LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);

        BigDecimal total = ventaRepository.totalVentas(
                inicioMes.atStartOfDay(),
                inicioMesSiguiente.atStartOfDay()
        );

        return total != null ? total : BigDecimal.ZERO;
    }

    public long getProductosStockBajo() {
        return productoRepository.findProductosStockBajo().size();
    }

    public long getAlertasNoLeidas() {
        return alertaRepository.contarNoLeidas();
    }

    public long getCreditosVencidos() {

        return ventaRepository.findCreditos()
                .stream()
                .filter(v ->
                        !v.estaCancelado()
                                && v.getFechaLimiteCredito() != null
                                && v.getFechaLimiteCredito().isBefore(LocalDateTime.now()))
                .count();
    }

    public BigDecimal getTotalCuentasPorPagar() {

        return cuentaPorPagarRepository.findPendientes()
                .stream()
                .map(c -> c.getSaldoPendiente())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCreditosPendientes() {

        return ventaRepository.findCreditos()
                .stream()
                .filter(v -> !v.estaCancelado())
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public double getVariacionVentasHoy() {

        BigDecimal hoy = getTotalVentasHoy();
        BigDecimal ayer = getTotalVentasAyer();

        if (ayer.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        return hoy.subtract(ayer)
                .divide(ayer, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public Map<LocalDate, BigDecimal> getVentasUltimos7Dias() {

        LocalDateTime desde = LocalDate.now()
                .minusDays(6)
                .atStartOfDay();

        List<Object[]> rows = ventaRepository.ventasPorDia(desde);

        Map<LocalDate, BigDecimal> resultado = new LinkedHashMap<>();

        for (int i = 6; i >= 0; i--) {
            resultado.put(LocalDate.now().minusDays(i), BigDecimal.ZERO);
        }

        for (Object[] row : rows) {

            LocalDate fecha;

            if (row[0] instanceof LocalDate ld) {
                fecha = ld;
            } else if (row[0] instanceof Date sqlDate) {
                fecha = sqlDate.toLocalDate();
            } else if (row[0] instanceof Timestamp ts) {
                fecha = ts.toLocalDateTime().toLocalDate();
            } else {
                continue;
            }

            BigDecimal total = (BigDecimal) row[1];

            resultado.put(fecha, total);
        }

        return resultado;
    }

    public List<Object[]> getTopProductosMes() {

        LocalDateTime desde = LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay();

        return ventaRepository.topProductos(desde)
                .stream()
                .limit(5)
                .toList();
    }

    public List<Venta> getUltimasVentas() {

        return ventaRepository.findActivas()
                .stream()
                .limit(5)
                .toList();
    }
}