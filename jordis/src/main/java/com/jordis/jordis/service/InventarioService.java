package com.jordis.jordis.service;

import com.jordis.jordis.model.AjusteInventario;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.repository.AjusteInventarioRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventarioService {

    private final ProductoRepository productoRepository;
    private final AjusteInventarioRepository ajusteRepository;
    private final VentaRepository ventaRepository;

    // Días a analizar para la recomendación
    private static final int DIAS_ANALISIS = 30;
    // Días de cobertura que queremos tener al comprar
    private static final int DIAS_COBERTURA = 15;

    public List<Producto> obtenerInventario() {
        return productoRepository.findByActivoTrue();
    }

    public List<Producto> obtenerStockBajo() {
        return productoRepository.findProductosStockBajo();
    }

    public Producto obtenerProductoPorId(Integer idProducto) {
        return productoRepository.findById(idProducto)
                .orElseThrow(() -> new RuntimeException(
                        "Producto no encontrado: " + idProducto));
    }

    /**
     * Calcula cuántas unidades se recomienda comprar de un producto.
     * Fórmula: (ventas últimos 30 días / 30) × 15 días de cobertura − stock actual
     * Si el resultado es negativo no hace falta comprar.
     */
    public int calcularRecomendacionCompra(Producto producto) {
        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_ANALISIS);
        int totalVendido = ventaRepository.totalVendidoDesde(
                producto.getIdProducto(), desde);

        double ventasPorDia = (double) totalVendido / DIAS_ANALISIS;
        int necesidad = (int) Math.ceil(ventasPorDia * DIAS_COBERTURA);
        int recomendado = necesidad - producto.getStock();

        return Math.max(recomendado, 0);
    }

    /**
     * Ajuste manual: el admin corrige el stock con un motivo.
     * cantidad positiva = entrada, cantidad negativa = salida.
     */
    @Transactional
    public void ajustarStock(Integer idProducto, Integer idUsuario,
                             int cantidad, String motivo) {

        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        int nuevoStock = producto.getStock() + cantidad;
        if (nuevoStock < 0) {
            throw new RuntimeException(
                    "No se puede reducir el stock por debajo de 0. " +
                            "Stock actual: " + producto.getStock());
        }

        producto.setStock(nuevoStock);
        productoRepository.save(producto);

        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setProducto(producto);
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(idUsuario);
        ajuste.setUsuario(usuario);
        ajuste.setCantidad(cantidad);
        ajuste.setMotivo(motivo);
        ajuste.setFechaHora(LocalDateTime.now());
        ajusteRepository.save(ajuste);

        log.info("Ajuste inventario — Producto: {} | Cantidad: {} | Nuevo stock: {} | Motivo: {}",
                producto.getNombre(), cantidad, nuevoStock, motivo);
    }

    @Transactional
    public void actualizarPreciosSugeridos(BigDecimal factor) {
        List<Producto> productos = productoRepository.findByActivoTrue();
        for (Producto p : productos) {
            if (p.getUltimoPrecioCompra() != null) {
                p.calcularPrecioSugerido(factor);
                productoRepository.save(p);
            }
        }
        log.info("Precios sugeridos actualizados con factor {}", factor);
    }

    @Transactional
    public void actualizarPrecioSugeridoProducto(Integer idProducto, BigDecimal factor) {
        productoRepository.findById(idProducto).ifPresent(p -> {
            if (p.getUltimoPrecioCompra() != null) {
                p.calcularPrecioSugerido(factor);
                productoRepository.save(p);
                log.info("Precio sugerido de '{}' actualizado con factor {}",
                        p.getNombre(), factor);
            }
        });
    }
}