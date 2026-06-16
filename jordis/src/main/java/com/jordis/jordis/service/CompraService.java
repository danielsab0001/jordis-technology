package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.CompraRepository;
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
                                  Map<Integer, BigDecimal[]> detalles) {

        Proveedor proveedor = proveedorService.obtenerPorId(idProveedor);

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFechaPedido(LocalDateTime.now());
        compra.setEstado("PENDIENTE");

        // Usar un usuario placeholder — se inyecta desde el controller
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(idUsuario);
        compra.setUsuario(usuario);

        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Integer, BigDecimal[]> entry : detalles.entrySet()) {
            Integer idProducto   = entry.getKey();
            Integer cantidad     = entry.getValue()[0].intValue();
            BigDecimal costo     = entry.getValue()[1];

            Producto producto = productoRepository.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + idProducto));

            CompraProducto detalle = new CompraProducto();
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setCostoUnitario(costo);
            detalle.setSubtotal(costo.multiply(BigDecimal.valueOf(cantidad)));
            compra.getDetalles().add(detalle);

            total = total.add(detalle.getSubtotal());
        }

        compra.setTotalCompra(total);
        Compra guardada = compraRepository.save(compra);
        log.info("Compra #{} registrada — PENDIENTE — Total: {}", guardada.getIdCompra(), total);
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
}