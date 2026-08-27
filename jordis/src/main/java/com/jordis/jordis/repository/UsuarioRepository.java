package com.jordis.jordis.repository;

import com.jordis.jordis.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    @Query("SELECT u FROM Usuario u WHERE LOWER(u.nombre) = LOWER(:nombre) AND u.activo = true")
    Optional<Usuario> findByNombreActivo(String nombre);

    @Query("SELECT u FROM Usuario u WHERE u.activo = true")
    List<Usuario> findActivos();

    @Query("SELECT u FROM Usuario u WHERE u.bloqueado = true")
    List<Usuario> findBloqueados();

    @Query("SELECT COUNT(u) > 0 FROM Usuario u WHERE LOWER(u.nombre) = LOWER(:nombre)")
    boolean existeNombre(String nombre);

    @Query("SELECT COUNT(u) > 0 FROM Usuario u WHERE LOWER(u.nombre) = LOWER(:nombre) AND u.idUsuario <> :id")
    boolean existeNombreEnOtro(String nombre, Integer id);

    @Query("SELECT u FROM Usuario u WHERE LOWER(u.nombre) = LOWER(:nombre)")
    Optional<Usuario> findByNombre(String nombre);
}