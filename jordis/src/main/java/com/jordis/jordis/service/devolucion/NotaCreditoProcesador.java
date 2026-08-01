package com.jordis.jordis.service.devolucion;

import com.jordis.jordis.model.Devolucion;
import com.jordis.jordis.model.MovimientoCaja;
import com.jordis.jordis.model.TipoDevolucion;
import com.jordis.jordis.model.TipoMovimientoCaja;
import com.jordis.jordis.repository.MovimientoCajaRepository;
import com.jordis.jordis.service.NCFService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Genera una Nota de Crédito Fiscal (NCF tipo B04) cuando la venta
 * original tenía comprobante fiscal — la venta original NUNCA se
 * modifica ni pierde su propio NCF; el B04 queda asociado solo a la
 * devolución, tal como exige la normativa de la DGII (RD).
 */
@Component
@RequiredArgsConstructor
public class NotaCreditoProcesador implements ProcesadorDevolucion {

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final NCFService ncfService;

    private static final String TIPO_NCF_NOTA_CREDITO = "B04";

    @Override
    public TipoDevolucion getTipo() {
        return TipoDevolucion.NOTA_CREDITO;
    }

    @Override
    public void procesar(Devolucion devolucion) {
        boolean ventaEsFiscal = Boolean.TRUE.equals(
                devolucion.getVenta().getEsCreditoFiscal());

        devolucion.setRequiereNcf(ventaEsFiscal);

        if (ventaEsFiscal) {
            String ncf = ncfService.generarNCF(TIPO_NCF_NOTA_CREDITO);
            devolucion.setNcfNotaCredito(ncf);
        }

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setTipo(TipoMovimientoCaja.NOTA_CREDITO);
        movimiento.setMonto(java.math.BigDecimal.ZERO);
        movimiento.setReferenciaTipo("DEVOLUCION");
        movimiento.setReferenciaId(devolucion.getIdDevolucion());
        movimiento.setDescripcion((ventaEsFiscal
                ? "Nota de crédito fiscal " + devolucion.getNcfNotaCredito()
                : "Nota de crédito interna (venta sin NCF)")
                + " — Factura " + devolucion.getVenta().getNumeroFactura());
        movimiento.setUsuario(devolucion.getUsuario());
        movimientoCajaRepository.save(movimiento);
    }
}