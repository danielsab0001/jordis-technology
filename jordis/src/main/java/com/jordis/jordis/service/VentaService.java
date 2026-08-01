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
    private final AuditoriaService auditoriaService;
    private final AutenticacionService autenticacionService;
    private final AlertaService alertaService;
    private final NCFService ncfService;
    private final JdbcTemplate jdbcTemplate;
    private final DevolucionRepository devolucionRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    public List<Venta> obtenerTodas() {
        return ventaRepository.findActivas();
    }

    public List<Venta> obtenerTodasIncluyendoAnuladas() {
        return ventaRepository.findTodasOrdenadas();
    }

    private static final int TAMANO_PAGINA_VENTAS = 15;

    public com.jordis.jordis.util.Pagina<Venta> obtenerPaginaVentas(int numeroPagina, String textoBusqueda) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(numeroPagina, TAMANO_PAGINA_VENTAS);
        org.springframework.data.domain.Page<Venta> pagina =
                ventaRepository.buscarPaginado(textoBusqueda, pageable);
        return new com.jordis.jordis.util.Pagina<>(
                pagina.getContent(), numeroPagina, pagina.getTotalPages(), pagina.getTotalElements());
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
                                BigDecimal itbisPorcentual,
                                BigDecimal montoSaldoAfavorAplicado) {

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

// ITBIS (se desglosa siempre que haya una tasa aplicable)
        if (itbisPorcentual != null
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

        // Aplicar saldo a favor del cliente, si corresponde (proveniente de
        // devoluciones anteriores — Nota de Crédito / Saldo a Favor).
        BigDecimal aplicado = montoSaldoAfavorAplicado != null
                ? montoSaldoAfavorAplicado : BigDecimal.ZERO;
        if (aplicado.compareTo(BigDecimal.ZERO) > 0) {
            if (cliente == null) {
                throw new VentaInvalidaException(
                        "Debes seleccionar un cliente para aplicar su saldo a favor.");
            }
            if (aplicado.compareTo(cliente.getSaldoAFavor()) > 0) {
                throw new VentaInvalidaException(
                        "El cliente no tiene suficiente saldo a favor. Disponible: RD$"
                                + cliente.getSaldoAFavor().toPlainString());
            }
            if (aplicado.compareTo(total) > 0) {
                throw new VentaInvalidaException(
                        "El monto aplicado de saldo a favor no puede ser mayor al total de la venta.");
            }
            cliente.setSaldoAFavor(cliente.getSaldoAFavor().subtract(aplicado));
            clienteRepository.save(cliente);
            venta.setMontoSaldoAfavorAplicado(aplicado);
            if (aplicado.compareTo(total) == 0) {
                venta.setMetodoPago("SALDO_A_FAVOR");
            }
        }

        Venta guardada = ventaRepository.save(venta);
        log.info("Venta #{} registrada — Factura: {} — Total: {} — Cajero: {}",
                guardada.getIdVenta(), numeroFactura,
                guardada.getTotal(), cajero.getNombreCompleto());

        auditoriaService.registrar(cajero, "VENTA_REGISTRADA", "Venta",
                guardada.getIdVenta(),
                "Factura " + numeroFactura + " — RD$" + guardada.getTotal().toPlainString());

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
    public Venta anularVenta(Integer idVenta, String motivo) {
        return anularVenta(idVenta, motivo, false);
    }

    @Transactional
    public Venta anularVenta(Integer idVenta, String motivo, boolean porProblemaProducto) {
        Venta venta = obtenerPorId(idVenta);
        if (venta.getAnulada()) {
            throw new VentaInvalidaException("Esta venta ya fue anulada.");
        }

        BigDecimal montoCobrado = Boolean.TRUE.equals(venta.getEsCredito())
                ? venta.getTotalPagado()
                : venta.getTotal();

        boolean creditoConAbonos = Boolean.TRUE.equals(venta.getEsCredito())
                && venta.getTotalPagado().compareTo(BigDecimal.ZERO) > 0;

        boolean requiereNotaCredito = (porProblemaProducto || creditoConAbonos)
                && montoCobrado.compareTo(BigDecimal.ZERO) > 0;

        if (requiereNotaCredito) {
            Cliente cliente = venta.getCliente();
            if (cliente == null) {
                throw new VentaInvalidaException(
                        "Esta anulación generaría saldo a favor, pero la venta no tiene "
                                + "un cliente identificado (venta ocasional). Selecciona un "
                                + "cliente en la venta antes de anular por esta razón.");
            }
            cliente.setSaldoAFavor(cliente.getSaldoAFavor().add(montoCobrado));
            clienteRepository.save(cliente);

            String ncfNotaCredito = null;
            if (Boolean.TRUE.equals(venta.getEsCreditoFiscal())) {
                ncfNotaCredito = ncfService.generarNCF("B04");
                venta.setNcfNotaCreditoAnulacion(ncfNotaCredito);
            }

            MovimientoCaja movimiento = new MovimientoCaja();
            movimiento.setTipo(TipoMovimientoCaja.NOTA_CREDITO);
            movimiento.setMonto(BigDecimal.ZERO);
            movimiento.setReferenciaTipo("VENTA_ANULADA");
            movimiento.setReferenciaId(venta.getIdVenta());
            movimiento.setDescripcion((ncfNotaCredito != null
                    ? "Nota de crédito fiscal " + ncfNotaCredito
                    : "Nota de crédito interna (venta sin NCF)")
                    + " por anulación (" + (porProblemaProducto
                    ? "problema con el producto" : "error de cajero, con abonos previos")
                    + ") — RD$" + montoCobrado.toPlainString()
                    + " — Factura " + venta.getNumeroFactura());
            movimiento.setUsuario(autenticacionService.getUsuarioActivo());
            movimientoCajaRepository.save(movimiento);

            log.info("Venta #{} anulada — RD${} convertidos en saldo a favor del cliente {}"
                            + " — Motivo: {} — NCF: {}",
                    idVenta, montoCobrado, cliente.getNombreCompleto(),
                    porProblemaProducto ? "problema con el producto" : "error de cajero",
                    ncfNotaCredito);
        }

        // Restaurar stock

        for (VentaProducto detalle : venta.getDetalles()) {
            Integer yaDevuelto = devolucionRepository.cantidadYaDevuelta(
                    venta.getIdVenta(), detalle.getProducto().getIdProducto());
            int cantidadARestaurar = detalle.getCantidad()
                    - (yaDevuelto != null ? yaDevuelto : 0);
            if (cantidadARestaurar <= 0) {
                continue;
            }
            Producto producto = detalle.getProducto();
            producto.setStock(producto.getStock() + cantidadARestaurar);
            try {
                productoRepository.saveAndFlush(producto);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConflictoConcurrenciaException(
                        "El stock de '" + producto.getNombre()
                                + "' cambió justo ahora. Vuelve a intentar anular la venta.");
            }
        }
        venta.setAnulada(true);
        venta.setEstado(EstadoVenta.ANULADA);
        venta.setMotivoAnulacion(motivo);
        ventaRepository.save(venta);
        log.info("Venta #{} anulada. Motivo: {}", idVenta, motivo);

        auditoriaService.registrar(
                autenticacionService.getUsuarioActivo(),
                "VENTA_ANULADA", "Venta", idVenta,
                "Factura " + venta.getNumeroFactura()
                        + " — Total: RD$" + venta.getTotal().toPlainString()
                        + (requiereNotaCredito
                        ? " — RD$" + montoCobrado.toPlainString()
                        + " convertidos a saldo a favor"
                        + (venta.getNcfNotaCreditoAnulacion() != null
                        ? " (NCF " + venta.getNcfNotaCreditoAnulacion() + ")"
                        : "")
                        : "")
                        + " — Motivo: " + motivo
                        + (porProblemaProducto ? " (problema con el producto)" : ""));

        return venta;
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