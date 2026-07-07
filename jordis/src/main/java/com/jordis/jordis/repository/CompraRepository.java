package com.jordis.jordis.repository;

import com.jordis.jordis.model.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Integer> {

    @Query("SELECT c FROM Compra c ORDER BY c.fechaPedido DESC")
    List<Compra> findTodas();

    @Query("SELECT c FROM Compra c WHERE c.proveedor.idProveedor = :idProveedor " +
            "ORDER BY c.fechaPedido DESC")
    List<Compra> findByProveedor(Integer idProveedor);

    @Query("SELECT c FROM Compra c WHERE c.estado = :estado ORDER BY c.fechaPedido DESC")
    List<Compra> findByEstado(String estado);
}