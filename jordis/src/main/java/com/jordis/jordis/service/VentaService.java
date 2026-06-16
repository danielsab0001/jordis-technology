package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.repository.VentaRepository;
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
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;

    public List<Venta> obtenerTodas() {
        return ventaRepository.findActivas();
    }

    /**
     * Registra una venta completa.
     * @param idCliente puede ser null (cliente ocasional sin registro)
     * @param cajero usuario que realiza la venta
     * @param metodoPago EFECTIVO, TARJETA o TRANSFERENCIA
     * @param descuentoPorcentual 0 a 100
     * @param items Map<idProducto, cantidad>
     */
    @Transactional
    public Venta registrarVenta(Integer idCliente, Usuario cajero,
                                String metodoPago, BigDecimal descuentoPorcentual,
                                Map<Integer, Integer> items) {

        if (items.isEmpty()) {
            throw new RuntimeException("La venta debe tener al menos un producto.");
        }

        Venta venta = new Venta();
        venta.setFechaHora(LocalDateTime.now());
        venta.setCajero(cajero);
        venta.setMetodoPago(metodoPago);
        venta.setDescuentoPorcentual(descuentoPorcentual);
        venta.setAnulada(false);

        if (idCliente != null) {
            Cliente cliente = new Cliente();
            cliente.setIdCliente(idCliente);
            venta.setCliente(cliente);
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            Integer idProducto = entry.getKey();
            Integer cantidad   = entry.getValue();

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

            // Descontar stock
            producto.setStock(producto.getStock() - cantidad);
            productoRepository.save(producto);
        }

        venta.setSubtotal(subtotal);

        // Aplicar descuento porcentual al total
        BigDecimal factorDescuento = BigDecimal.ONE.subtract(
                descuentoPorcentual.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal total = subtotal.multiply(factorDescuento)
                .setScale(2, RoundingMode.HALF_UP);
        venta.setTotal(total);

        Venta guardada = ventaRepository.save(venta);
        log.info("Venta #{} registrada — Total: {} — Cajero: {}",
                guardada.getIdVenta(), total, cajero.getNombreCompleto());
        return guardada;
    }

    @Transactional
    public void anularVenta(Integer idVenta, String motivo) {
        Venta venta = ventaRepository.findById(idVenta)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + idVenta));

        if (venta.getAnulada()) {
            throw new RuntimeException("Esta venta ya fue anulada.");
        }

        // Restaurar stock
        for (VentaProducto detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStock(producto.getStock() + detalle.getCantidad());
            productoRepository.save(producto);
        }

        venta.setAnulada(true);
        venta.setMotivoAnulacion(motivo);
        ventaRepository.save(venta);
        log.info("Venta #{} anulada — Motivo: {}", idVenta, motivo);
    }

    public static class StockInsuficienteException extends RuntimeException {
        public StockInsuficienteException(String msg) { super(msg); }
    }
}