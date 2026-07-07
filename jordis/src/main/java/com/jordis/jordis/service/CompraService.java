package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.CompraEdicionRepository;
import com.jordis.jordis.repository.CompraRepository;
import com.jordis.jordis.repository.CuentaPorPagarRepository;
import com.jordis.jordis.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final ProveedorService proveedorService;
    private final AlertaService alertaService;
    private final CompraEdicionRepository edicionRepository;
    private final CuentaPorPagarService cuentaPorPagarService;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    // Margen de ganancia por defecto: 30%
    private static final BigDecimal MARGEN_DEFAULT = new BigDecimal("1.30");

    public List<Compra> obtenerTodas() {
        return compraRepository.findTodas();
    }

    public List<Compra> obtenerPendientes() {
        return compraRepository.findByEstado("PENDIENTE");
    }

    /**
     * Registra una nueva orden de compra en estado PENDIENTE.
     * detalles: Map<idProducto, [cantidad, costoUnitario]>
     */
    @Transactional
    public Compra registrarCompra(Integer idProveedor, Integer idUsuario,
                                  Map<Integer, BigDecimal[]> detalles,
                                  String descripcion) {
        Proveedor proveedor = proveedorService.obtenerPorId(idProveedor);

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFechaPedido(LocalDateTime.now());
        compra.setEstado("PENDIENTE");
        compra.setDescripcion(descripcion); // ← línea nueva

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(idUsuario);
        compra.setUsuario(usuario);

        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Integer, BigDecimal[]> entry : detalles.entrySet()) {
            Integer idProducto = entry.getKey();
            Integer cantidad   = entry.getValue()[0].intValue();
            BigDecimal costo   = entry.getValue()[1];

            Producto producto = productoRepository.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException(
                            "Producto no encontrado: " + idProducto));

            CompraProducto detalle = new CompraProducto();
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setCantidadPedida(cantidad);
            detalle.setCostoUnitario(costo);
            detalle.setSubtotal(costo.multiply(BigDecimal.valueOf(cantidad)));
            compra.getDetalles().add(detalle);
            total = total.add(detalle.getSubtotal());
        }

        compra.setTotalCompra(total);
        Compra guardada = compraRepository.save(compra);
        log.info("Compra #{} registrada — PENDIENTE — Total: {}",
                guardada.getIdCompra(), total);
        return guardada;
    }

    /**
     * Marca la compra como RECIBIDA:
     * - Aumenta el stock de cada producto
     * - Actualiza el último precio de compra
     * - Calcula el precio sugerido de venta
     */
    @Transactional
    public Compra recibirCompra(Integer idCompra) {
        Compra compra = compraRepository.findById(idCompra)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada: " + idCompra));

        if (!"PENDIENTE".equals(compra.getEstado())) {
            throw new RuntimeException("Solo se pueden recibir compras en estado PENDIENTE.");
        }

        for (CompraProducto detalle : compra.getDetalles()) {
            Producto producto = detalle.getProducto();

            // Aumentar stock
            producto.setStock(producto.getStock() + detalle.getCantidad());

            // Actualizar último precio de compra
            producto.setUltimoPrecioCompra(detalle.getCostoUnitario());

            // Calcular precio sugerido con margen del 30%
            producto.calcularPrecioSugerido(MARGEN_DEFAULT);

            // Generar alerta si el precio cambió mucho
            alertaService.alertaPrecioCompraInusual(
                    producto,
                    producto.getUltimoPrecioCompra(), // precio anterior
                    detalle.getCostoUnitario());       // precio nuevo

            productoRepository.save(producto);
            log.info("Stock actualizado — Producto: {} | Nuevo stock: {} | Precio sugerido: {}",
                    producto.getNombre(), producto.getStock(), producto.getPrecioSugerido());
        }

        compra.setEstado("RECIBIDA");
        compra.setFechaRecepcion(LocalDateTime.now());
        Compra actualizada = compraRepository.save(compra);
        log.info("Compra #{} marcada como RECIBIDA", idCompra);

        return actualizada;
    }

    @Transactional
    public void cancelarCompra(Integer idCompra) {
        Compra compra = compraRepository.findById(idCompra)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada: " + idCompra));
        if (!"PENDIENTE".equals(compra.getEstado())) {
            throw new RuntimeException("Solo se pueden cancelar compras PENDIENTES.");
        }
        compra.setEstado("CANCELADA");
        compraRepository.save(compra);
        log.info("Compra #{} cancelada", idCompra);
    }

    @Transactional
    public void editarCompra(Integer idCompra,
                             Map<Integer, Integer> cantidadesRecibidas,
                             String motivo, boolean notaCredito,
                             Integer idUsuario) {

        Compra compra = compraRepository.findById(idCompra)
                .orElseThrow(() -> new RuntimeException(
                        "Compra no encontrada: " + idCompra));

        if ("RECIBIDA".equals(compra.getEstado())) {
            throw new RuntimeException(
                    "No se puede editar una compra ya recibida.");
        }
        if ("CANCELADA".equals(compra.getEstado())) {
            throw new RuntimeException(
                    "No se puede editar una compra cancelada.");
        }

        StringBuilder cambios = new StringBuilder();

        for (CompraProducto detalle : compra.getDetalles()) {
            Integer idProducto   = detalle.getProducto().getIdProducto();
            Integer cantRecibida = cantidadesRecibidas.getOrDefault(
                    idProducto, detalle.getCantidad());

            // Preservar la cantidad pedida original
            int cantPedida = detalle.getCantidadPedida() != null
                    ? detalle.getCantidadPedida()
                    : detalle.getCantidad();

            if (!cantRecibida.equals(detalle.getCantidad())) {
                cambios.append(String.format(
                        "%s: pedido %d → recibido %d. ",
                        detalle.getProducto().getNombre(),
                        detalle.getCantidadPedida(),
                        cantRecibida));

                detalle.setCantidad(cantRecibida);
                detalle.setSubtotal(
                        detalle.getCostoUnitario()
                                .multiply(BigDecimal.valueOf(cantRecibida)));
            }
        }

        // Recalcular total con cantidades recibidas
        BigDecimal nuevoTotal = compra.getDetalles().stream()
                .map(CompraProducto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        compra.setTotalCompra(nuevoTotal);
        compraRepository.save(compra);

        // Registrar la edición
        CompraEdicion edicion = new CompraEdicion();
        edicion.setCompra(compra);
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(idUsuario);
        edicion.setUsuario(usuario);
        edicion.setMotivo(motivo);
        edicion.setCambios(cambios.length() > 0
                ? cambios.toString() : "Sin cambios en cantidades.");
        edicion.setNotaCredito(notaCredito);
        edicionRepository.save(edicion);

        log.info("Compra #{} editada. Cambios: {}", idCompra, cambios);

        // Actualizar cuenta por pagar si existe
        cuentaPorPagarRepository.findByCompra(idCompra).ifPresent(cuenta -> {
            if (!cuenta.estaCancelada()) {
                cuenta.setMontoTotal(nuevoTotal);
                cuentaPorPagarRepository.save(cuenta);
                log.info("Cuenta por pagar #{} actualizada a RD${}",
                        cuenta.getIdCuenta(), nuevoTotal);
            }
        });
    }
}