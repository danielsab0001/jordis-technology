package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.CuentaPagoRepository;
import com.jordis.jordis.repository.CuentaPorPagarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CuentaPorPagarService {

    private final CuentaPorPagarRepository cuentaRepository;
    private final CuentaPagoRepository     pagoRepository;
    private final CierreCajaService        cierreCajaService;

    public List<CuentaPorPagar> obtenerTodas() {
        return cuentaRepository.findTodas();
    }

    public List<CuentaPorPagar> obtenerPendientes() {
        return cuentaRepository.findPendientes();
    }

    public CuentaPorPagar obtenerPorId(Integer id) {
        return cuentaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Cuenta por pagar no encontrada: " + id));
    }

    // Crea la cuenta por pagar automáticamente al recibir una compra
    @Transactional
    public CuentaPorPagar crearDesdCompra(Compra compra,
                                          LocalDateTime fechaLimite,
                                          String notas) {
        // Si ya existe una cuenta para esta compra, no crear otra
        if (cuentaRepository.findByCompra(compra.getIdCompra()).isPresent()) {
            log.warn("Ya existe cuenta por pagar para compra #{}",
                    compra.getIdCompra());
            return cuentaRepository.findByCompra(compra.getIdCompra()).get();
        }

        CuentaPorPagar cuenta = new CuentaPorPagar();
        cuenta.setCompra(compra);
        cuenta.setProveedor(compra.getProveedor());
        cuenta.setMontoTotal(compra.getTotalCompra());
        cuenta.setFechaEmision(LocalDateTime.now());
        cuenta.setFechaLimite(fechaLimite);
        cuenta.setEstado("PENDIENTE");
        cuenta.setNotas(notas);

        CuentaPorPagar guardada = cuentaRepository.save(cuenta);
        log.info("Cuenta por pagar creada — Compra #{} — Proveedor: {} — Total: RD${}",
                compra.getIdCompra(),
                compra.getProveedor().getNombre(),
                compra.getTotalCompra());
        return guardada;
    }

    @Transactional
    public CuentaPago registrarPago(Integer idCuenta, BigDecimal monto,
                                    String metodoPago, boolean pagadoDesdeCaja,
                                    String notas, Usuario cajero, LocalDateTime fechaPago) {
        CuentaPorPagar cuenta = obtenerPorId(idCuenta);

        if (cuenta.estaCancelada()) {
            throw new RuntimeException("Esta cuenta ya está completamente pagada.");
        }

        if ("EFECTIVO".equals(metodoPago) && pagadoDesdeCaja
                && !cierreCajaService.hayCajaAbierta()) {
            throw new RuntimeException(
                    "No hay ninguna caja abierta. Debes abrir la caja antes de pagar en "
                            + "efectivo desde la gaveta (o marca que el efectivo es de otro origen).");
        }

        BigDecimal saldo = cuenta.getSaldoPendiente();
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto debe ser mayor a 0.");
        }
        if (monto.compareTo(saldo) > 0) {
            throw new RuntimeException(
                    "El monto excede el saldo pendiente de RD$" + saldo.toPlainString());
        }

        CuentaPago pago = new CuentaPago();
        pago.setCuenta(cuenta);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setPagadoDesdeCaja(pagadoDesdeCaja);
        pago.setNotas(notas);
        pago.setCajero(cajero);
        pago.setFechaPago(fechaPago);

        CuentaPago guardado = pagoRepository.save(pago);

        if (cuenta.estaCancelada()) {
            cuenta.setEstado("PAGADA");
            cuentaRepository.save(cuenta);
        }

        log.info("Pago registrado — Cuenta #{} — Monto: RD${} — Método: {}{} — Saldo restante: RD${}",
                idCuenta, monto, metodoPago,
                "EFECTIVO".equals(metodoPago)
                        ? (pagadoDesdeCaja ? " (de la caja)" : " (de otro origen, NO de la caja)")
                        : "",
                cuenta.getSaldoPendiente());
        return guardado;
    }
}