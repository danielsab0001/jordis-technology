package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.ClienteRepository;
import com.jordis.jordis.repository.DevolucionRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.repository.VentaRepository;
import com.jordis.jordis.service.devolucion.ProcesadorDevolucion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registra devoluciones parciales o totales sobre una venta ya facturada,
 * sin modificarla nunca. Responsabilidades separadas de VentaService a
 * propósito: anular una venta y devolver productos son procesos de
 * negocio distintos con reglas y efectos distintos (ver arquitectura).
 *
 * Aplica el Reglamento 293-11 de la DGII: una devolución dentro de los
 * 30 días de emitida la factura reembolsa el valor + ITBIS; después de
 * 30 días, el ITBIS facturado originalmente ya no se puede devolver, así
 * que solo se reembolsa el valor neto del producto.
 */
@Service
@Slf4j
public class DevolucionService {

    private static final int DIAS_LIMITE_ITBIS = 30;

    private final DevolucionRepository devolucionRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final AuditoriaService auditoriaService;
    private final AutenticacionService autenticacionService;
    private final Map<TipoDevolucion, ProcesadorDevolucion> procesadores;

    public DevolucionService(DevolucionRepository devolucionRepository,
                             ProductoRepository productoRepository,
                             ClienteRepository clienteRepository,
                             VentaRepository ventaRepository,
                             AuditoriaService auditoriaService,
                             AutenticacionService autenticacionService,
                             List<ProcesadorDevolucion> procesadoresDisponibles) {
        this.devolucionRepository = devolucionRepository;
        this.productoRepository = productoRepository;
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
        this.auditoriaService = auditoriaService;
        this.autenticacionService = autenticacionService;
        this.procesadores = procesadoresDisponibles.stream()
                .collect(Collectors.toMap(ProcesadorDevolucion::getTipo, Function.identity()));
    }

    public List<Devolucion> obtenerTodas() {
        return devolucionRepository.findTodasOrdenadas();
    }

    public List<Devolucion> obtenerPorVenta(Integer idVenta) {
        return devolucionRepository.findActivasPorVenta(idVenta);
    }

    public Devolucion obtenerPorId(Integer idDevolucion) {
        return devolucionRepository.findById(idDevolucion)
                .orElseThrow(() -> new RuntimeException(
                        "Devolución no encontrada: " + idDevolucion));
    }

    /**
     * Cantidad máxima que todavía se puede devolver de un producto dentro
     * de una venta (lo vendido menos lo ya devuelto en devoluciones activas).
     */
    public int obtenerCantidadDisponibleParaDevolver(VentaProducto lineaVenta) {
        Integer yaDevuelto = devolucionRepository.cantidadYaDevuelta(
                lineaVenta.getVenta().getIdVenta(),
                lineaVenta.getProducto().getIdProducto());
        return lineaVenta.getCantidad() - (yaDevuelto != null ? yaDevuelto : 0);
    }

    /** true si la venta todavía está dentro de los 30 días para reembolsar el ITBIS. */
    public boolean estaDentroDelPlazoDeItbis(Venta venta) {
        long dias = ChronoUnit.DAYS.between(venta.getFechaHora(), LocalDateTime.now());
        return dias <= DIAS_LIMITE_ITBIS;
    }

    /**
     * Determina automáticamente el tipo de devolución. No es una elección
     * del cajero: la decide la ley. Si la venta original tenía NCF fiscal,
     * la devolución debe respaldarse con una Nota de Crédito Fiscal (B04);
     * si no, es un simple saldo a favor interno sin comprobante fiscal.
     */
    public TipoDevolucion determinarTipoDevolucion(Venta venta) {
        return Boolean.TRUE.equals(venta.getEsCreditoFiscal())
                ? TipoDevolucion.NOTA_CREDITO
                : TipoDevolucion.SALDO_A_FAVOR;
    }

    /**
     * Registra una devolución de uno o varios productos de una venta.
     *
     * @param venta      venta original (debe existir y no estar anulada)
     * @param items      Map<idProducto, cantidad a devolver>
     * @param motivo     motivo de la devolución (obligatorio)
     * @param observaciones notas adicionales, opcional
     */
    @Transactional
    public Devolucion registrarDevolucion(Venta venta,
                                          Map<Integer, Integer> items,
                                          String motivo,
                                          String observaciones) {

        if (venta.estaAnulada()) {
            throw new DevolucionInvalidaException(
                    "No se puede registrar una devolución sobre una venta anulada.");
        }
        if (items == null || items.isEmpty()) {
            throw new DevolucionInvalidaException(
                    "Debes seleccionar al menos un producto a devolver.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new DevolucionInvalidaException(
                    "Debes indicar el motivo de la devolución.");
        }

        if (venta.getCliente() == null) {
            throw new DevolucionInvalidaException(
                    "Esta devolución requiere un cliente identificado en la venta "
                            + "(no aplica para clientes ocasionales).");
        }

        TipoDevolucion tipo = determinarTipoDevolucion(venta);
        ProcesadorDevolucion procesador = procesadores.get(tipo);
        if (procesador == null) {
            throw new DevolucionInvalidaException(
                    "Tipo de devolución no soportado: " + tipo);
        }

        Usuario usuario = autenticacionService.getUsuarioActivo();

        boolean dentroDePlazo = estaDentroDelPlazoDeItbis(venta);
        boolean ventaTieneItbis = venta.getItbisPorcentual() != null
                && venta.getItbisPorcentual().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal factorNeto = BigDecimal.ONE; // por defecto, sin ajuste
        boolean incluyeItbis = true;
        if (ventaTieneItbis && !dentroDePlazo) {
            BigDecimal tasa = venta.getItbisPorcentual()
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            factorNeto = BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(tasa), 6, RoundingMode.HALF_UP);
            incluyeItbis = false;
        }

        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(venta);
        devolucion.setUsuario(usuario);
        devolucion.setFechaHora(LocalDateTime.now());
        devolucion.setMotivo(motivo);
        devolucion.setTipoDevolucion(tipo);
        devolucion.setObservaciones(observaciones);
        devolucion.setEstado(EstadoDevolucion.REGISTRADA);
        devolucion.setIncluyeItbis(incluyeItbis);

        BigDecimal montoTotal = BigDecimal.ZERO;

        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            Integer idProducto = entry.getKey();
            Integer cantidadDevuelta = entry.getValue();

            if (cantidadDevuelta == null || cantidadDevuelta <= 0) {
                throw new DevolucionInvalidaException(
                        "La cantidad a devolver debe ser mayor a 0.");
            }

            VentaProducto lineaVenta = venta.getDetalles().stream()
                    .filter(vp -> vp.getProducto().getIdProducto().equals(idProducto))
                    .findFirst()
                    .orElseThrow(() -> new DevolucionInvalidaException(
                            "El producto no pertenece a esta venta."));

            int disponible = obtenerCantidadDisponibleParaDevolver(lineaVenta);
            if (cantidadDevuelta > disponible) {
                throw new DevolucionInvalidaException(
                        "No puedes devolver " + cantidadDevuelta + " unidades de '"
                                + lineaVenta.getProducto().getNombre()
                                + "'. Disponible para devolver: " + disponible);
            }

            BigDecimal precioUnitario = lineaVenta.getPrecioUnitario();
            BigDecimal subtotalBruto = precioUnitario
                    .multiply(BigDecimal.valueOf(cantidadDevuelta));

            BigDecimal subtotalLinea = subtotalBruto
                    .multiply(factorNeto)
                    .setScale(2, RoundingMode.HALF_UP);

            DevolucionDetalle detalle = new DevolucionDetalle();
            detalle.setDevolucion(devolucion);
            detalle.setProducto(lineaVenta.getProducto());
            detalle.setCantidad(cantidadDevuelta);
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setSubtotal(subtotalLinea);
            devolucion.getDetalles().add(detalle);

            montoTotal = montoTotal.add(subtotalLinea);

            Producto producto = lineaVenta.getProducto();
            producto.setStock(producto.getStock() + cantidadDevuelta);
            try {
                productoRepository.saveAndFlush(producto);
            } catch (ObjectOptimisticLockingFailureException ex) {
                throw new ConflictoConcurrenciaException(
                        "El stock de '" + producto.getNombre()
                                + "' cambió justo ahora. Vuelve a intentar la devolución.");
            }
        }

        devolucion.setMontoTotal(montoTotal.setScale(2, RoundingMode.HALF_UP));

        Devolucion guardada = devolucionRepository.save(devolucion);

        procesador.procesar(guardada);

        guardada = devolucionRepository.save(guardada);

        Cliente cliente = venta.getCliente();
        cliente.setSaldoAFavor(cliente.getSaldoAFavor().add(guardada.getMontoTotal()));
        clienteRepository.save(cliente);

        log.info("Devolución #{} registrada — Venta: {} — Tipo: {} — Monto: RD${}"
                        + " — Incluye ITBIS: {} — Saldo a favor del cliente: RD${} — Usuario: {}",
                guardada.getIdDevolucion(), venta.getNumeroFactura(), tipo,
                guardada.getMontoTotal(), incluyeItbis, cliente.getSaldoAFavor(),
                usuario.getNombreCompleto());

        auditoriaService.registrar(usuario, "DEVOLUCION_REGISTRADA", "Devolucion",
                guardada.getIdDevolucion(),
                "Factura " + venta.getNumeroFactura()
                        + " — Tipo: " + tipo
                        + " — RD$" + guardada.getMontoTotal().toPlainString()
                        + (incluyeItbis ? "" : " (fuera del plazo de 30 días, sin ITBIS)")
                        + " — Motivo: " + motivo);

        boolean noQuedaNadaPorDevolver = venta.getDetalles().stream()
                .allMatch(linea -> obtenerCantidadDisponibleParaDevolver(linea) == 0);

        if (noQuedaNadaPorDevolver && !venta.estaAnulada()) {
            venta.setAnulada(true);
            venta.setEstado(EstadoVenta.ANULADA);
            venta.setMotivoAnulacion(
                    "Anulada automáticamente: se devolvió el 100% de los productos de la venta.");
            ventaRepository.save(venta);

            log.info("Venta #{} anulada automáticamente — se devolvió el 100% de sus productos.",
                    venta.getIdVenta());
            auditoriaService.registrar(usuario, "VENTA_ANULADA", "Venta", venta.getIdVenta(),
                    "Factura " + venta.getNumeroFactura()
                            + " — Anulada automáticamente: devolución total de productos"
                            + " (última devolución #" + guardada.getIdDevolucion() + ").");
        }

        return guardada;
    }

    // ---- Excepciones ----
    public static class DevolucionInvalidaException extends RuntimeException {
        public DevolucionInvalidaException(String msg) { super(msg); }
    }
    public static class ConflictoConcurrenciaException extends RuntimeException {
        public ConflictoConcurrenciaException(String msg) { super(msg); }
    }
}