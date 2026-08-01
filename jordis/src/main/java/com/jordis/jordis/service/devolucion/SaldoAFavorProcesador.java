package com.jordis.jordis.service.devolucion;

import com.jordis.jordis.model.Devolucion;
import com.jordis.jordis.model.MovimientoCaja;
import com.jordis.jordis.model.TipoDevolucion;
import com.jordis.jordis.model.TipoMovimientoCaja;
import com.jordis.jordis.repository.MovimientoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SaldoAFavorProcesador implements ProcesadorDevolucion {

    private final MovimientoCajaRepository movimientoCajaRepository;

    @Override
    public TipoDevolucion getTipo() {
        return TipoDevolucion.SALDO_A_FAVOR;
    }

    @Override
    public void procesar(Devolucion devolucion) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setTipo(TipoMovimientoCaja.SALDO_A_FAVOR);
        movimiento.setMonto(java.math.BigDecimal.ZERO);
        movimiento.setReferenciaTipo("DEVOLUCION");
        movimiento.setReferenciaId(devolucion.getIdDevolucion());
        movimiento.setDescripcion("Saldo a favor generado — RD$"
                + devolucion.getMontoTotal().toPlainString()
                + " — Factura " + devolucion.getVenta().getNumeroFactura());
        movimiento.setUsuario(devolucion.getUsuario());
        movimientoCajaRepository.save(movimiento);
    }
}