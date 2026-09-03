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
    private final AlertaService alertaService;
    private final AuditoriaService auditoriaService;

    private static final int MAX_INTENTOS = 3;

    private static final int SESION_ABANDONADA_MINUTOS = 20;

    private Usuario usuarioActivo;

    // Sin @Transactional aquí — cada operación maneja su propia transacción
    public Usuario autenticar(String nombreUsuario, String contrasena) {

        // 1. Buscar por nombre de usuario sin filtrar por activo
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombreUsuario(nombreUsuario);

        if (usuarioOpt.isEmpty()) {
            log.warn("Login fallido: usuario '{}' no existe", nombreUsuario);
            throw new UsuarioNoEncontradoException("Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarioOpt.get();

        // 2. Verificar si la cuenta está desactivada
        if (!usuario.getActivo()) {
            log.warn("Login fallido: cuenta desactivada '{}'", nombreUsuario);
            throw new CuentaDesactivadaException(
                    "Esta cuenta ha sido desactivada. Comunícate con el administrador."
            );
        }

        // 3. Verificar si está bloqueado (solo cajeros)
        if (usuario.getBloqueado()
                && usuario.getRol() != Usuario.Rol.ADMINISTRADOR) {
            log.warn("Login fallido: cuenta bloqueada '{}'", nombreUsuario);
            throw new UsuarioBloqueadoException(
                    "Esta cuenta ha sido bloqueada por precaución debido a varios " +
                            "intentos fallidos de contraseña. Comuníquese con el administrador."
            );
        }

        // 4. Verificar contraseña
        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {

            // Administradores no acumulan intentos
            if (usuario.getRol() == Usuario.Rol.ADMINISTRADOR) {
                log.warn("Contraseña incorrecta para admin '{}'", nombreUsuario);
                throw new CredencialesInvalidasException("Contraseña incorrecta.");
            }

            // Cajeros: incrementar contador en transacción separada
            int nuevoIntentos = usuario.getIntentosFallidos() + 1;

            if (nuevoIntentos >= MAX_INTENTOS) {
                // Bloquear en transacción propia para que no haga rollback
                bloquearUsuario(usuario.getIdUsuario());
                log.warn("Usuario '{}' bloqueado tras {} intentos", nombreUsuario, nuevoIntentos);
                alertaService.alertaUsuarioBloqueado(usuario);
                throw new UsuarioBloqueadoException(
                        "Esta cuenta ha sido bloqueada por precaución debido a varios " +
                                "intentos fallidos de contraseña. Comuníquese con el administrador."
                );
            }

            // Guardar nuevo contador en transacción propia
            incrementarIntentos(usuario.getIdUsuario(), nuevoIntentos);
            int restantes = MAX_INTENTOS - nuevoIntentos;
            log.warn("Contraseña incorrecta para '{}'. Intentos restantes: {}",
                    nombreUsuario, restantes);
            throw new CredencialesInvalidasException(
                    "Contraseña incorrecta. Te quedan " + restantes + " intento(s)."
            );
        }

        // 5. Login exitoso — resetear intentos en transacción propia
        resetearIntentos(usuario.getIdUsuario());

        // 6. Sesión única: rechazar si ya hay una sesión activa y
        // reciente de este mismo usuario en OTRA máquina. Si es la misma
        // máquina (típicamente: la app se cerró de golpe y se reabrió),
        // se permite de inmediato sin esperar el umbral de abandono.
        String maquinaActual = obtenerNombreMaquina();
        boolean mismaMaquina = maquinaActual.equals(usuario.getSesionMaquina());

        if (Boolean.TRUE.equals(usuario.getSesionActiva())
                && !mismaMaquina
                && !sesionEstaAbandonada(usuario)) {
            log.warn("Login rechazado: '{}' ya tiene una sesión activa en otra máquina ({})",
                    nombreUsuario, usuario.getSesionMaquina());
            throw new SesionActivaException(
                    "Este usuario ya tiene una sesión abierta en otra computadora. "
                            + "Cierra esa sesión primero, o espera unos minutos si esa "
                            + "sesión quedó abierta por un cierre inesperado.");
        }

        abrirSesion(usuario.getIdUsuario(), maquinaActual);
        usuarioActivo = usuario;
        log.info("Login exitoso: {} ({}) desde '{}'",
                usuario.getNombreCompleto(), usuario.getRol(), maquinaActual);
        return usuario;
    }

    private boolean sesionEstaAbandonada(Usuario usuario) {
        if (usuario.getSesionActualizadaEn() == null) return true;
        return usuario.getSesionActualizadaEn()
                .isBefore(java.time.LocalDateTime.now()
                        .minusMinutes(SESION_ABANDONADA_MINUTOS));
    }

    private String obtenerNombreMaquina() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "desconocida";
        }
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
        Usuario desbloqueado = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado."));
        desbloqueado.setBloqueado(false);
        desbloqueado.setIntentosFallidos(0);
        usuarioRepository.save(desbloqueado);
        log.info("Usuario ID {} desbloqueado", idUsuario);

        auditoriaService.registrar(
                usuarioActivo, "USUARIO_DESBLOQUEADO", "Usuario", idUsuario,
                "Usuario desbloqueado: " + desbloqueado.getNombreCompleto());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abrirSesion(Integer idUsuario, String maquina) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setSesionActiva(true);
            u.setSesionMaquina(maquina);
            u.setSesionIniciadaEn(java.time.LocalDateTime.now());
            u.setSesionActualizadaEn(java.time.LocalDateTime.now());
            usuarioRepository.save(u);
        });
    }

    // Se llama periódicamente (cada pocos minutos) mientras la app está
    // abierta, para que otras instancias sepan que esta sesión sigue viva.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void actualizarHeartbeat(Integer idUsuario) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setSesionActualizadaEn(java.time.LocalDateTime.now());
            usuarioRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberarSesion(Integer idUsuario) {
        usuarioRepository.findById(idUsuario).ifPresent(u -> {
            u.setSesionActiva(false);
            usuarioRepository.save(u);
        });
    }

    public void cerrarSesion() {
        log.info("Cerrando sesión de: {}",
                usuarioActivo != null ? usuarioActivo.getNombreCompleto() : "desconocido");
        if (usuarioActivo != null) {
            liberarSesion(usuarioActivo.getIdUsuario());
        }
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
    public static class SesionActivaException extends RuntimeException {
        public SesionActivaException(String msg) { super(msg); }
    }
}