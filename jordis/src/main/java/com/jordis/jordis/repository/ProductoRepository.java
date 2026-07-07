package com.jordis.jordis.repository;

import com.jordis.jordis.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByActivoTrue();

    List<Producto> findByNombreContainingIgnoreCaseAndActivoTrue(String nombre);

    List<Producto> findByCategoriaIdCategoriaAndActivoTrue(Integer idCategoria);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stock <= p.stockMinimo")
    List<Producto> findProductosStockBajo();

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
            "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "LOWER(p.marca)  LIKE LOWER(CONCAT('%', :texto, '%')))")
    List<Producto> buscarPorNombreOMarca(String texto);
}