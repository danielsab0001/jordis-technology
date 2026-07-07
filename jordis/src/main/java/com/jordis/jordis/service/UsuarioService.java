package com.jordis.jordis.service;

import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // Retorna TODOS: activos, inactivos y bloqueados
    // para que el admin pueda ver y gestionar todos
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    public List<Usuario> obtenerBloqueados() {
        return usuarioRepository.findBloqueados();
    }

    @Transactional
    public Usuario crear(String nombre, String apellido,
                         String contrasena, Usuario.Rol rol) {

        if (usuarioRepository.existeNombre(nombre)) {
            throw new UsuarioYaExisteException(
                    "Ya existe un usuario con el nombre '" + nombre + "'."
            );
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos(0);

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario creado: {}", guardado.getNombreCompleto());
        return guardado;
    }

    @Transactional
    public Usuario actualizar(Integer idUsuario, String nombre,
                              String apellido, Usuario.Rol rol) {

        Usuario usuario = obtenerPorId(idUsuario);

        if (usuarioRepository.existeNombreEnOtro(nombre, idUsuario)) {
            throw new UsuarioYaExisteException(
                    "Ya existe otro usuario con el nombre '" + nombre + "'."
            );
        }

        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);

        log.info("Usuario actualizado: {}", usuario.getNombreCompleto());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void cambiarContrasena(Integer idUsuario, String nuevaContrasena) {
        Usuario usuario = obtenerPorId(idUsuario);
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);
        log.info("Contraseña cambiada para: {}", usuario.getNombreCompleto());
    }

    @Transactional
    public void desactivar(Integer idUsuario) {
        Usuario usuario = obtenerPorId(idUsuario);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        log.info("Usuario desactivado: {}", usuario.getNombreCompleto());
    }

    @Transactional
    public void reactivar(Integer idUsuario) {
        Usuario usuario = obtenerPorId(idUsuario);
        usuario.setActivo(true);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);
        log.info("Usuario reactivado: {}", usuario.getNombreCompleto());
    }

    @Transactional
    public void desbloquear(Integer idUsuario) {
        Usuario usuario = obtenerPorId(idUsuario);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);
        log.info("Usuario desbloqueado: {}", usuario.getNombreCompleto());
    }

    public Usuario obtenerPorId(Integer idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new UsuarioNoEncontradoException(
                        "Usuario con ID " + idUsuario + " no encontrado."
                ));
    }

    public static class UsuarioYaExisteException extends RuntimeException {
        public UsuarioYaExisteException(String msg) { super(msg); }
    }

    public static class UsuarioNoEncontradoException extends RuntimeException {
        public UsuarioNoEncontradoException(String msg) { super(msg); }
    }
}