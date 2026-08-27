package com.jordis.jordis.repository;

import com.jordis.jordis.model.CreditoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditoPagoRepository extends JpaRepository<CreditoPago, Integer> {

    @Query("SELECT p FROM CreditoPago p WHERE p.venta.idVenta = :idVenta " +
            "ORDER BY p.fechaPago DESC")
    List<CreditoPago> findByVenta(Integer idVenta);

    @Query("SELECT p FROM CreditoPago p " +
            "WHERE p.fechaPago BETWEEN :desde AND :hasta")
    List<CreditoPago> findByFechaPagoBetween(LocalDateTime desde, LocalDateTime hasta);
}