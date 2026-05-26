package com.jordis.jordis.repository;

import com.jordis.jordis.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByNombreAndActivoTrue(String nombre);

    boolean existsByNombre(String nombre);
}