package com.jordis.jordis.repository;

import com.jordis.jordis.model.CuentaPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuentaPagoRepository extends JpaRepository<CuentaPago, Integer> {

    @Query("SELECT p FROM CuentaPago p WHERE p.cuenta.idCuenta = :idCuenta " +
            "ORDER BY p.fechaPago DESC")
    List<CuentaPago> findByCuenta(Integer idCuenta);
}