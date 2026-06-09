package com.jordis.jordis.service;

import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final int MAX_INTENTOS = 3;

    private Usuario usuarioActivo;

    // Sin @Transactional aquí — cada operación maneja su propia transacción
    public Usuario autenticar(String nombre, String contrasena) {

        // 1. Buscar por nombre sin filtrar por activo
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombre(nombre);

        if (usuarioOpt.isEmpty()) {
            log.warn("Login fallido: usuario '{}' no existe", nombre);
            throw new UsuarioNoEncontradoException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarioOpt.get();

        // 2. Verificar si la cuenta está desactivada
        if (!usuario.getActivo()) {
            log.warn("Login fallido: cuenta desactivada '{}'", nombre);
            throw new CuentaDesactivadaException(
                    "Esta cuenta ha sido desactivada. Comunícate con el administrador."
            );
        }

        // 3. Verificar si está bloqueado (solo cajeros)
        if (usuario.getBloqueado()
                && usuario.getRol() != Usuario.Rol.ADMINISTRADOR) {
            log.warn("Login fallido: cuenta bloqueada '{}'", nombre);
            throw new UsuarioBloqueadoException(
                    "Esta cuenta ha sido bloqueada por precaución debido a varios " +
                            "intentos fallidos de contraseña. Comuníquese con el administrador."
            );
        }

        // 4. Verificar contraseña
        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {

            // Administradores no acumulan intentos
            if (usuario.getRol() == Usuario.Rol.ADMINISTRADOR) {
                log.warn("Contraseña incorrecta para admin '{}'", nombre);
                throw new CredencialesInvalidasException("Contraseña incorrecta.");
            }

            // Cajeros: incrementar contador en transacción separada
            int nuevoIntentos = usuario.getIntentosFallidos() + 1;

            if (nuevoIntentos >= MAX_INTENTOS) {
                // Bloquear en transacción propia para que no haga rollback
                bloquearUsuario(usuario.getIdUsuario());
                log.warn("Usuario '{}' bloqueado tras {} intentos", nombre, nuevoIntentos);
                throw new UsuarioBloqueadoException(
                        "Esta cuenta ha sido bloqueada por precaución debido a varios " +
                                "intentos fallidos de contraseña. Comuníquese con el administrador."
                );
            }

            // Guardar nuevo contador en transacción propia
            incrementarIntentos(usuario.getIdUsuario(), nuevoIntentos);
            int restantes = MAX_INTENTOS - nuevoIntentos;
            log.warn("Contraseña incorrecta para '{}'. Intentos restantes: {}",
                    nombre, restantes);
            throw new CredencialesInvalidasException(
                    "Contraseña incorrecta. Te quedan " + restantes + " intento(s)."
            );
        }

        // 5. Login exitoso — resetear en transacción propia
        resetearIntentos(usuario.getIdUsuario());
        usuarioActivo = usuario;
        log.info("Login exitoso: {} ({})", usuario.getNombreCompleto(), usuario.getRol());
        return usuario;
    }

    // ---- Métodos con transacción propia ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementarIntentos(Integer idUsuario, int nuevoIntentos) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setIntentosFallidos(nuevoIntentos);
            usuarioRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bloquearUsuario(Integer idUsuario) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setIntentosFallidos(MAX_INTENTOS);
            u.setBloqueado(true);
            usuarioRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetearIntentos(Integer idUsuario) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setIntentosFallidos(0);
            u.setBloqueado(false);
            usuarioRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void desbloquearUsuario(Integer idUsuario) {
        usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado."));
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setBloqueado(false);
            u.setIntentosFallidos(0);
            usuarioRepository.save(u);
        });
        log.info("Usuario ID {} desbloqueado", idUsuario);
    }

    public void cerrarSesion() {
        log.info("Cerrando sesión de: {}",
                usuarioActivo != null ? usuarioActivo.getNombreCompleto() : "desconocido");
        usuarioActivo = null;
    }

    public Usuario getUsuarioActivo() { return usuarioActivo; }
    public boolean hayUsuarioActivo() { return usuarioActivo != null; }

    // ---- Excepciones ----
    public static class UsuarioBloqueadoException extends RuntimeException {
        public UsuarioBloqueadoException(String msg) { super(msg); }
    }
    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException(String msg) { super(msg); }
    }
    public static class UsuarioNoEncontradoException extends RuntimeException {
        public UsuarioNoEncontradoException(String msg) { super(msg); }
    }
    public static class CuentaDesactivadaException extends RuntimeException {
        public CuentaDesactivadaException(String msg) { super(msg); }
    }
}