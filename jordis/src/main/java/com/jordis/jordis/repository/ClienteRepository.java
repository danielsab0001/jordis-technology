package com.jordis.jordis.repository;

import com.jordis.jordis.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    @Query("SELECT c FROM Cliente c WHERE c.activo = true")
    List<Cliente> findActivos();

    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
            "(LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            " LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%')))")
    List<Cliente> buscarPorNombreOApellido(String texto);

    @Query("SELECT c FROM Cliente c WHERE c.cedulaIdentificacion = :cedula AND c.activo = true")
    Optional<Cliente> findByCedula(String cedula);

    @Query("SELECT COUNT(c) > 0 FROM Cliente c WHERE c.cedulaIdentificacion = :cedula")
    boolean existeCedula(String cedula);

    @Query("SELECT COUNT(c) > 0 FROM Cliente c WHERE c.cedulaIdentificacion = :cedula AND c.idCliente <> :id")
    boolean existeCedulaEnOtro(String cedula, Integer id);
}