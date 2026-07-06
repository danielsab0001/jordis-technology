package com.jordis.jordis.repository;

import com.jordis.jordis.model.CuentaPorPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {

    @Query("SELECT c FROM CuentaPorPagar c ORDER BY c.fechaEmision DESC")
    List<CuentaPorPagar> findTodas();

    @Query("SELECT c FROM CuentaPorPagar c WHERE c.estado = 'PENDIENTE' " +
            "ORDER BY c.fechaLimite ASC")
    List<CuentaPorPagar> findPendientes();

    @Query("SELECT c FROM CuentaPorPagar c WHERE c.compra.idCompra = :idCompra")
    Optional<CuentaPorPagar> findByCompra(Integer idCompra);
}