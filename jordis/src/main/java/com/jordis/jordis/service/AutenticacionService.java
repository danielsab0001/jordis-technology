package com.jordis.jordis.service;

import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // Máximo de intentos antes de bloquear
    private static final int MAX_INTENTOS = 3;

    // Mapa en memoria: nombre de usuario cantidad de intentos fallidos
    private final Map<String, Integer> intentosFallidos = new HashMap<>();

    // Sesión activa actual
    private Usuario usuarioActivo;

    /**
     * Intenta autenticar a un usuario.
     * @return el Usuario si las credenciales son válidas
     * @throws UsuarioNoEncontradoException si el usuario no existe o está inactivo
     * @throws CredencialesInvalidasException si la contraseña es incorrecta
     * @throws UsuarioBloqueadoException si superó el límite de intentos
     */
    public Usuario autenticar(String nombre, String contrasena) {

        // 1. Verificar si el usuario está bloqueado
        if (estaBloqueado(nombre)) {
            log.warn("Intento de acceso a usuario bloqueado: {}", nombre);
            throw new UsuarioBloqueadoException(
                    "El usuario '" + nombre + "' está bloqueado por demasiados intentos fallidos. " +
                            "Contacte al administrador."
            );
        }

        // 2. Buscar el usuario en la base de datos
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombreAndActivoTrue(nombre);

        if (usuarioOpt.isEmpty()) {
            log.warn("Intento de login con usuario inexistente o inactivo: {}", nombre);
            registrarIntentoFallido(nombre);
            throw new UsuarioNoEncontradoException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarioOpt.get();

        // 3. Verificar contraseña con BCrypt
        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {
            registrarIntentoFallido(nombre);
            int intentosRestantes = MAX_INTENTOS - intentosFallidos.getOrDefault(nombre, 0);
            log.warn("Contraseña incorrecta para usuario: {}. Intentos restantes: {}", nombre, intentosRestantes);

            if (intentosRestantes <= 0) {
                throw new UsuarioBloqueadoException(
                        "Has superado el límite de intentos. El usuario ha sido bloqueado."
                );
            }

            throw new CredencialesInvalidasException(
                    "Contraseña incorrecta. Te quedan " + intentosRestantes + " intento(s)."
            );
        }

        // 4. Login exitoso — limpiar intentos y guardar sesión
        intentosFallidos.remove(nombre);
        usuarioActivo = usuario;
        log.info("Login exitoso: {} ({})", usuario.getNombreCompleto(), usuario.getRol());
        return usuario;
    }

    public void cerrarSesion() {
        log.info("Cerrando sesión de: {}", usuarioActivo != null ? usuarioActivo.getNombreCompleto() : "desconocido");
        usuarioActivo = null;
    }

    public Usuario getUsuarioActivo() {
        return usuarioActivo;
    }

    public boolean hayUsuarioActivo() {
        return usuarioActivo != null;
    }

    // ---- Métodos privados ----

    private boolean estaBloqueado(String nombre) {
        return intentosFallidos.getOrDefault(nombre, 0) >= MAX_INTENTOS;
    }

    private void registrarIntentoFallido(String nombre) {
        intentosFallidos.merge(nombre, 1, Integer::sum);
        log.warn("Intentos fallidos para '{}': {}", nombre, intentosFallidos.get(nombre));
    }

    // ---- Excepciones internas ----

    public static class UsuarioBloqueadoException extends RuntimeException {
        public UsuarioBloqueadoException(String msg) { super(msg); }
    }

    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException(String msg) { super(msg); }
    }

    public static class UsuarioNoEncontradoException extends RuntimeException {
        public UsuarioNoEncontradoException(String msg) { super(msg); }
    }
}