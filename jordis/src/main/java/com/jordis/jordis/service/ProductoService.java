package com.jordis.jordis.service;

import com.jordis.jordis.model.Categoria;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.repository.CategoriaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;
    private final AutenticacionService autenticacionService;

    public List<Producto> obtenerTodos() {
        return productoRepository.findByActivoTrue();
    }

    public List<Producto> buscar(String texto) {
        if (texto == null || texto.isBlank()) return obtenerTodos();
        return productoRepository.buscarPorNombreOMarca(texto);
    }

    public List<Producto> obtenerPorCategoria(Integer idCategoria) {
        return productoRepository.findByCategoriaIdCategoriaAndActivoTrue(idCategoria);
    }

    public List<Producto> obtenerStockBajo() {
        return productoRepository.findProductosStockBajo();
    }

    public List<Categoria> obtenerCategorias() {
        return categoriaRepository.findAll();
    }

    @Transactional
    public Producto crear(String nombre, String descripcion, BigDecimal precio,
                          Integer stock, Integer stockMinimo, Categoria categoria,
                          String marca, String modelo) {

        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecioUnitario(precio);
        producto.setStock(stock);
        producto.setStockMinimo(stockMinimo);
        producto.setCategoria(categoria);
        producto.setMarca(marca);
        producto.setModelo(modelo);
        producto.setActivo(true);

        log.info("Producto creado: {}", nombre);
        Producto guardado = productoRepository.save(producto);
        auditoriaService.registrar(autenticacionService.getUsuarioActivo(),
                "PRODUCTO_CREADO", "Producto", guardado.getIdProducto(), nombre);
        return guardado;
    }

    @Transactional
    public Producto actualizar(Integer id, String nombre, String descripcion,
                               BigDecimal precio, Integer stock, Integer stockMinimo,
                               Categoria categoria, String marca, String modelo) {

        Producto producto = obtenerPorId(id);
        BigDecimal precioAnterior = producto.getPrecioUnitario();

        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecioUnitario(precio);
        producto.setStock(stock);
        producto.setStockMinimo(stockMinimo);
        producto.setCategoria(categoria);
        producto.setMarca(marca);
        producto.setModelo(modelo);

        Producto guardado = productoRepository.save(producto);
        log.info("Producto actualizado: {}", nombre);

        if (precioAnterior != null && precio != null
                && precioAnterior.compareTo(precio) != 0) {
            auditoriaService.registrar(
                    autenticacionService.getUsuarioActivo(),
                    "PRECIO_MODIFICADO", "Producto", id,
                    "'" + nombre + "': RD$" + precioAnterior.toPlainString()
                            + " → RD$" + precio.toPlainString());
        }

        return guardado;
    }

    @Transactional
    public void eliminar(Integer id) {
        Producto producto = obtenerPorId(id);
        producto.setActivo(false);
        productoRepository.save(producto);
        log.info("Producto eliminado (lógico): {}", producto.getNombre());
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(
                        "Producto con ID " + id + " no encontrado."));
    }

    public static class ProductoNoEncontradoException extends RuntimeException {
        public ProductoNoEncontradoException(String msg) { super(msg); }
    }
}