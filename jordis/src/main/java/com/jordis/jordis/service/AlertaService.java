package com.jordis.jordis.service;

import com.jordis.jordis.model.AlertaSistema;
import com.jordis.jordis.model.Producto;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.model.Venta;
import com.jordis.jordis.repository.AlertaSistemaRepository;
import com.jordis.jordis.repository.ProductoRepository;
import com.jordis.jordis.repository.UsuarioRepository;
import com.jordis.jordis.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertaService {

    private final AlertaSistemaRepository alertaRepository;
    private final ProductoRepository      productoRepository;
    private final UsuarioRepository       usuarioRepository;
    private final VentaRepository ventaRepository;

    private static final BigDecimal UMBRAL_PRECIO_DIFERENCIA = new BigDecimal("20");

    public List<AlertaSistema> obtenerNoLeidas() {
        return alertaRepository.findNoLeidas();
    }

    public List<AlertaSistema> obtenerTodas() {
        return alertaRepository.findTodas();
    }

    public long contarNoLeidas() {
        return alertaRepository.contarNoLeidas();
    }

    @Transactional
    public void marcarLeida(Integer idAlerta) {
        alertaRepository.findById(idAlerta).ifPresent(a -> {
            a.setLeida(true);
            alertaRepository.save(a);
        });
    }

    @Transactional
    public void marcarTodasLeidas() {
        alertaRepository.findNoLeidas().forEach(a -> {
            a.setLeida(true);
            alertaRepository.save(a);
        });
    }

    @Transactional
    public void eliminar(Integer idAlerta) {
        alertaRepository.deleteById(idAlerta);
    }

    // Alerta de usuario bloqueado — siempre crea una nueva
    @Transactional
    public void alertaUsuarioBloqueado(Usuario usuario) {
        AlertaSistema alerta = new AlertaSistema();
        alerta.setTipo("USUARIO_BLOQUEADO");
        alerta.setTitulo("Usuario bloqueado: " + usuario.getNombreCompleto());
        alerta.setDescripcion("El usuario '" + usuario.getNombre()
                + "' fue bloqueado tras superar el límite de intentos.");
        alerta.setIdReferencia(usuario.getIdUsuario());
        alertaRepository.save(alerta);
        log.info("Alerta: usuario bloqueado — {}", usuario.getNombreCompleto());
    }

    // Alerta de precio inusual al registrar compra
    @Transactional
    public void alertaPrecioCompraInusual(Producto producto,
                                          BigDecimal precioAnterior,
                                          BigDecimal precioNuevo) {
        if (precioAnterior == null || precioAnterior.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal diferencia = precioNuevo.subtract(precioAnterior).abs()
                .divide(precioAnterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (diferencia.compareTo(new BigDecimal("25")) >= 0) {
            AlertaSistema alerta = new AlertaSistema();
            alerta.setTipo("PRECIO_COMPRA_INUSUAL");
            alerta.setTitulo("Precio inusual en compra: " + producto.getNombre());
            alerta.setDescripcion(String.format(
                    "El precio de '%s' cambió un %.1f%%. Anterior: RD$%s → Nuevo: RD$%s",
                    producto.getNombre(), diferencia.floatValue(),
                    precioAnterior.toPlainString(), precioNuevo.toPlainString()));
            alerta.setIdReferencia(producto.getIdProducto());
            alertaRepository.save(alerta);
        }
    }

    /**
     * Escaneo completo: actualiza alertas existentes y elimina las que ya no aplican.
     * Llamar cuando el admin presiona "Escanear ahora".
     */
    @Transactional
    public void escanearTodo() {
        escanearStockBajo();
        escanearDiferenciaPrecios();
        limpiarAlertasUsuariosBloqueados();
        escanearCreditosPorVencer();
    }

    @Transactional
    public void escanearStockBajo() {
        List<Producto> todosActivos = productoRepository.findByActivoTrue();

        for (Producto p : todosActivos) {
            Optional<AlertaSistema> existente = alertaRepository.findNoLeidas()
                    .stream()
                    .filter(a -> "STOCK_BAJO".equals(a.getTipo())
                            && p.getIdProducto().equals(a.getIdReferencia()))
                    .findFirst();

            if (p.isStockBajo()) {
                // Actualizar o crear alerta
                String desc = "'" + p.getNombre() + "' tiene solo " + p.getStock()
                        + " unidades (mínimo: " + p.getStockMinimo() + ").";

                if (existente.isPresent()) {
                    // Actualizar la alerta con el stock actual
                    AlertaSistema a = existente.get();
                    a.setDescripcion(desc);
                    a.setLeida(false); // volver a no leída si el problema persiste
                    alertaRepository.save(a);
                } else {
                    AlertaSistema nueva = new AlertaSistema();
                    nueva.setTipo("STOCK_BAJO");
                    nueva.setTitulo("Stock bajo: " + p.getNombre());
                    nueva.setDescripcion(desc);
                    nueva.setIdReferencia(p.getIdProducto());
                    alertaRepository.save(nueva);
                }
            } else {
                // El stock ya está bien — eliminar la alerta si existía
                existente.ifPresent(a -> alertaRepository.delete(a));
            }
        }
    }

    @Transactional
    public void escanearDiferenciaPrecios() {
        List<Producto> productos = productoRepository.findByActivoTrue();

        for (Producto p : productos) {
            Optional<AlertaSistema> existente = alertaRepository.findNoLeidas()
                    .stream()
                    .filter(a -> "PRECIO_DIFERENCIA".equals(a.getTipo())
                            && p.getIdProducto().equals(a.getIdReferencia()))
                    .findFirst();

            if (p.getPrecioUnitario() == null || p.getPrecioSugerido() == null
                    || p.getPrecioSugerido().compareTo(BigDecimal.ZERO) == 0) {
                // Sin precio sugerido — eliminar alerta si existía
                existente.ifPresent(a -> alertaRepository.delete(a));
                continue;
            }

            BigDecimal diferencia = p.getPrecioUnitario()
                    .subtract(p.getPrecioSugerido()).abs()
                    .divide(p.getPrecioSugerido(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (diferencia.compareTo(UMBRAL_PRECIO_DIFERENCIA) >= 0) {
                String desc = String.format(
                        "Precio de venta de '%s' (RD$%s) difiere %.1f%% del sugerido (RD$%s).",
                        p.getNombre(), p.getPrecioUnitario().toPlainString(),
                        diferencia.floatValue(), p.getPrecioSugerido().toPlainString());

                if (existente.isPresent()) {
                    AlertaSistema a = existente.get();
                    a.setDescripcion(desc);
                    a.setLeida(false);
                    alertaRepository.save(a);
                } else {
                    AlertaSistema nueva = new AlertaSistema();
                    nueva.setTipo("PRECIO_DIFERENCIA");
                    nueva.setTitulo("Revisar precio: " + p.getNombre());
                    nueva.setDescripcion(desc);
                    nueva.setIdReferencia(p.getIdProducto());
                    alertaRepository.save(nueva);
                }
            } else {
                // La diferencia ya está dentro del rango — eliminar alerta
                existente.ifPresent(a -> alertaRepository.delete(a));
            }
        }
    }

    // Elimina alertas de usuarios bloqueados que ya fueron desbloqueados
    @Transactional
    public void limpiarAlertasUsuariosBloqueados() {
        List<AlertaSistema> alertasBloqueo = alertaRepository.findNoLeidas()
                .stream()
                .filter(a -> "USUARIO_BLOQUEADO".equals(a.getTipo()))
                .toList();

        for (AlertaSistema alerta : alertasBloqueo) {
            if (alerta.getIdReferencia() == null) continue;
            usuarioRepository.findById(alerta.getIdReferencia()).ifPresent(u -> {
                if (!u.getBloqueado()) {
                    // Usuario ya fue desbloqueado — eliminar la alerta
                    alertaRepository.delete(alerta);
                    log.info("Alerta de bloqueo eliminada para usuario: {}",
                            u.getNombreCompleto());
                }
            });
        }
    }

    // Alertar créditos que vencen pronto (7 días) o ya vencidos
    @Transactional
    public void escanearCreditosPorVencer() {
        // Todos los créditos activos, pagados o no
        List<Venta> todosCreditos = ventaRepository.findActivas().stream()
                .filter(Venta::getEsCredito)
                .filter(v -> v.getFechaLimiteCredito() != null)
                .toList();

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteAviso = ahora.plusDays(7);

        for (Venta v : todosCreditos) {

            Optional<AlertaSistema> existente = alertaRepository.findNoLeidas()
                    .stream()
                    .filter(a -> "CREDITO_VENCIMIENTO".equals(a.getTipo())
                            && v.getIdVenta().equals(a.getIdReferencia()))
                    .findFirst();

            // Si ya está pagado, eliminar la alerta y continuar
            if (v.estaCancelado()) {
                existente.ifPresent(alertaRepository::delete);
                continue;
            }

            boolean vencido   = v.getFechaLimiteCredito().isBefore(ahora);
            boolean porVencer = !vencido
                    && v.getFechaLimiteCredito().isBefore(limiteAviso);

            if (!vencido && !porVencer) {
                // Aún falta mucho para vencer — eliminar alerta si existía
                existente.ifPresent(alertaRepository::delete);
                continue;
            }

            String clienteNombre = v.getCliente() != null
                    ? v.getCliente().getNombreCompleto() : "Cliente desconocido";
            String fechaTexto = v.getFechaLimiteCredito().toLocalDate().toString();

            String titulo = vencido
                    ? "Crédito VENCIDO: " + clienteNombre
                    : "Crédito por vencer: " + clienteNombre;

            String desc = String.format(
                    "Factura %s — Saldo pendiente: RD$%s — %s: %s",
                    v.getNumeroFactura(),
                    v.getSaldoPendiente().toPlainString(),
                    vencido ? "Venció el" : "Vence el",
                    fechaTexto);

            if (existente.isPresent()) {
                AlertaSistema a = existente.get();
                a.setTitulo(titulo);
                a.setDescripcion(desc);
                a.setLeida(false);
                alertaRepository.save(a);
            } else {
                AlertaSistema nueva = new AlertaSistema();
                nueva.setTipo("CREDITO_VENCIMIENTO");
                nueva.setTitulo(titulo);
                nueva.setDescripcion(desc);
                nueva.setIdReferencia(v.getIdVenta());
                alertaRepository.save(nueva);
            }
        }
    }
}