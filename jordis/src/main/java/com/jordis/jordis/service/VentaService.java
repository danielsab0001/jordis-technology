package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VentaService {

    private final VentaRepository        ventaRepository;
    private final ProductoRepository     productoRepository;
    private final CreditoPagoRepository  creditoPagoRepository;
    private final ClienteRepository      clienteRepository;
    private final AlertaService alertaService;
    private final NCFService ncfService;
    private final JdbcTemplate jdbcTemplate;

    public List<Venta> obtenerTodas() {
        return ventaRepository.findActivas();
    }

    public List<Venta> obtenerCreditos() {
        return ventaRepository.findCreditos();
    }

    public List<Venta> filtrarEntreFechas(LocalDateTime desde, LocalDateTime hasta) {
        return ventaRepository.findEntreFechas(desde, hasta);
    }

    public List<Venta> filtrarPorCliente(Integer idCliente) {
        return ventaRepository.findByCliente(idCliente);
    }

    public List<Venta> filtrarPorCajero(Integer idCajero) {
        return ventaRepository.findByCajero(idCajero);
    }

    public Venta obtenerPorId(Integer idVenta) {
        return ventaRepository.findById(idVenta)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + idVenta));
    }

    /**
     * Registra una venta completa con garantías y soporte de crédito.
     *
     * @param idCliente           null = cliente ocasional
     * @param cajero              usuario que realiza la venta
     * @param metodoPago          EFECTIVO, TARJETA, TRANSFERENCIA, CREDITO
     * @param descuentoPorcentual 0-100
     * @param items               Map<idProducto, cantidad>
     * @param garantias           Map<idProducto, [descripcion, meses]>
     * @param notas               notas adicionales de la venta
     * @param esCredito           si la venta es a crédito
     * @param fechaLimiteCredito  fecha límite de pago (solo si esCredito=true)
     */
    @Transactional
    public Venta registrarVenta(Integer idCliente,
                                Usuario cajero,
                                String metodoPago,
                                BigDecimal descuentoPorcentual,
                                Map<Integer, Integer> items,
                                Map<Integer, String[]> garantias,
                                String notas,
                                boolean esCredito,
                                LocalDateTime fechaLimiteCredito,
                                boolean esCreditoFiscal,
                                String tipoNcf,
                                BigDecimal itbisPorcentual) {

        if (items.isEmpty()) {
            throw new VentaInvalidaException("La venta debe tener al menos un producto.");
        }

        // Validar cliente para crédito
        Cliente cliente = null;
        if (idCliente != null) {
            cliente = clienteRepository.findById(idCliente)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));
        }

        if (esCredito) {
            if (cliente == null) {
                throw new VentaInvalidaException(
                        "Las ventas a crédito requieren un cliente registrado.");
            }
            if (!cliente.esEmpresa()) {
                throw new VentaInvalidaException(
                        "Las ventas a crédito solo están disponibles para empresas.");
            }
            if (fechaLimiteCredito == null) {
                throw new VentaInvalidaException(
                        "Debes indicar la fecha límite de pago.");
            }
        }

        // Generar número de factura
        String numeroFactura = generarNumeroFactura();

        Venta venta = new Venta();
        venta.setNumeroFactura(numeroFactura);
        venta.setFechaHora(LocalDateTime.now());
        venta.setCajero(cajero);
        venta.setCliente(cliente);
        venta.setMetodoPago(esCredito ? "CREDITO" : metodoPago);
        venta.setDescuentoPorcentual(descuentoPorcentual);
        venta.setEsCredito(esCredito);
        venta.setFechaLimiteCredito(fechaLimiteCredito);
        venta.setNotas(notas);
        venta.setAnulada(false);

        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            Integer idProducto = entry.getKey();
            Integer cantidad = entry.getValue();

            Producto producto = productoRepository.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException(
                            "Producto no encontrado: " + idProducto));

            if (producto.getStock() < cantidad) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para '" + producto.getNombre()
                                + "'. Disponible: " + producto.getStock());
            }

            BigDecimal lineaSubtotal = producto.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            VentaProducto detalle = new VentaProducto();
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecioUnitario());
            detalle.setSubtotal(lineaSubtotal);
            venta.getDetalles().add(detalle);

            subtotal = subtotal.add(lineaSubtotal);

            producto.setStock(producto.getStock() - cantidad);
            try {
                productoRepository.saveAndFlush(producto);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConflictoConcurrenciaException(
                        "El stock de '" + producto.getNombre()
                                + "' cambió justo ahora — probablemente otra venta "
                                + "se registró al mismo tiempo. Vuelve a intentar la venta.");
            }

            // Agregar garantía si existe
            if (garantias != null && garantias.containsKey(idProducto)) {
                String[] g = garantias.get(idProducto);
                String descGarantia = g[0];
                int meses = g.length > 1 && g[1] != null
                        ? Integer.parseInt(g[1]) : 0;

                if (descGarantia != null && !descGarantia.isBlank()) {
                    VentaGarantia garantia = new VentaGarantia();
                    garantia.setVenta(venta);
                    garantia.setProducto(producto);
                    garantia.setDescripcion(descGarantia);
                    garantia.setMeses(meses);
                    if (meses > 0) {
                        garantia.setFechaVence(
                                LocalDateTime.now().plusMonths(meses));
                    }
                    venta.getGarantias().add(garantia);
                }
            }
        }

        // Calcular descuento
        BigDecimal descuento = subtotal.multiply(descuentoPorcentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Calcular total (el ITBIS ya está incluido en el precio)
        BigDecimal total = subtotal.subtract(descuento)
                .setScale(2, RoundingMode.HALF_UP);

        // Guardar valores en la venta
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setTotal(total);

        // NCF
        venta.setEsCreditoFiscal(esCreditoFiscal);

        if (esCreditoFiscal && tipoNcf != null) {
            String ncf = ncfService.generarNCF(tipoNcf);
            venta.setNcf(ncf);
            venta.setTipoNcf(tipoNcf);
        }

// ITBIS (solo se desglosa si hay comprobante fiscal)
        if (esCreditoFiscal
                && itbisPorcentual != null
                && itbisPorcentual.compareTo(BigDecimal.ZERO) > 0) {

            venta.setItbisPorcentual(itbisPorcentual);

            BigDecimal tasa = itbisPorcentual.divide(
                    BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

            BigDecimal divisor = BigDecimal.ONE.add(tasa);

            BigDecimal montoItbis = venta.getTotal()
                    .multiply(tasa)
                    .divide(divisor, 2, RoundingMode.HALF_UP);

            venta.setMontoItbis(montoItbis);

        } else {

            venta.setItbisPorcentual(BigDecimal.ZERO);
            venta.setMontoItbis(BigDecimal.ZERO);

        }

        Venta guardada = ventaRepository.save(venta);
        log.info("Venta #{} registrada — Factura: {} — Total: {} — Cajero: {}",
                guardada.getIdVenta(), numeroFactura,
                guardada.getTotal(), cajero.getNombreCompleto());
        return guardada;
    }

    /**
     * Registra un pago parcial o total de una venta a crédito.
     */
    @Transactional
    public CreditoPago registrarPagoCredito(Integer idVenta, BigDecimal monto,
                                            String metodoPago, String notas,
                                            Usuario cajero, LocalDateTime fechaPago) {
        Venta venta = obtenerPorId(idVenta);

        if (!venta.getEsCredito()) {
            throw new VentaInvalidaException("Esta venta no es a crédito.");
        }
        if (venta.getAnulada()) {
            throw new VentaInvalidaException("No se puede pagar una venta anulada.");
        }
        if (venta.estaCancelado()) {
            throw new VentaInvalidaException("Esta venta ya está completamente pagada.");
        }

        BigDecimal saldo = venta.getSaldoPendiente();
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new VentaInvalidaException("El monto debe ser mayor a 0.");
        }
        if (monto.compareTo(saldo) > 0) {
            throw new VentaInvalidaException(
                    "El monto excede el saldo pendiente de RD$" + saldo.toPlainString());
        }

        CreditoPago pago = new CreditoPago();
        pago.setVenta(venta);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setNotas(notas);
        pago.setCajero(cajero);
        pago.setFechaPago(fechaPago);

        CreditoPago guardado = creditoPagoRepository.save(pago);
        log.info("Pago de crédito registrado — Venta #{} — Monto: RD${} — Saldo restante: RD${}",
                idVenta, monto, venta.getSaldoPendiente().subtract(monto));
        alertaService.escanearCreditosPorVencer();
        return guardado;
    }

    @Transactional
    public void anularVenta(Integer idVenta, String motivo) {
        Venta venta = obtenerPorId(idVenta);
        if (venta.getAnulada()) {
            throw new VentaInvalidaException("Esta venta ya fue anulada.");
        }
        // Restaurar stock
        for (VentaProducto detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStock(producto.getStock() + detalle.getCantidad());
            try {
                productoRepository.saveAndFlush(producto);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConflictoConcurrenciaException(
                        "El stock de '" + producto.getNombre()
                                + "' cambió justo ahora. Vuelve a intentar anular la venta.");
            }
        }
        venta.setAnulada(true);
        venta.setMotivoAnulacion(motivo);
        ventaRepository.save(venta);
        log.info("Venta #{} anulada. Motivo: {}", idVenta, motivo);
    }

    private String generarNumeroFactura() {
        Long numero = jdbcTemplate.queryForObject(
                "SELECT nextval('factura_seq')", Long.class);
        return String.format("FAC-%05d", numero);
    }

    // ---- Excepciones ----
    public static class StockInsuficienteException extends RuntimeException {
        public StockInsuficienteException(String msg) { super(msg); }
    }
    public static class VentaInvalidaException extends RuntimeException {
        public VentaInvalidaException(String msg) { super(msg); }
    }
    public static class ConflictoConcurrenciaException extends RuntimeException {
        public ConflictoConcurrenciaException(String msg) { super(msg); }
    }
}