package com.jordis.jordis.repository;

import com.jordis.jordis.model.VentaGarantia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VentaGarantiaRepository extends JpaRepository<VentaGarantia, Integer> {

    @Query("SELECT g FROM VentaGarantia g WHERE g.venta.idVenta = :idVenta")
    List<VentaGarantia> findByVenta(Integer idVenta);
}