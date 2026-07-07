package com.jordis.jordis.repository;

import com.jordis.jordis.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    @Query("SELECT p FROM Proveedor p WHERE p.activo = true")
    List<Proveedor> findActivos();

    @Query("SELECT p FROM Proveedor p WHERE p.activo = true AND " +
            "LOWER(p.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))")
    List<Proveedor> buscarPorNombre(String texto);
}