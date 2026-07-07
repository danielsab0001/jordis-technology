package com.jordis.jordis.repository;

import com.jordis.jordis.model.CompraEdicion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompraEdicionRepository extends JpaRepository<CompraEdicion, Integer> {

    @Query("SELECT e FROM CompraEdicion e WHERE e.compra.idCompra = :idCompra " +
            "ORDER BY e.fechaHora DESC")
    List<CompraEdicion> findByCompra(Integer idCompra);
}