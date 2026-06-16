package com.jordis.jordis.repository;

import com.jordis.jordis.model.AjusteInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario, Integer> {

    @Query("SELECT a FROM AjusteInventario a WHERE a.producto.idProducto = :idProducto " +
            "ORDER BY a.fechaHora DESC")
    List<AjusteInventario> findByProducto(Integer idProducto);
}