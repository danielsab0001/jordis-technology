package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CierreCajaService {

    private final CierreCajaRepository   cierreCajaRepository;
    private final VentaRepository        ventaRepository;
    private final CreditoPagoRepository  creditoPagoRepository;
    private final CuentaPagoRepository   cuentaPagoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final DashboardService       dashboardService;
    private final AuditoriaService       auditoriaService;
    private final ConfiguracionService   configuracionService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private static final String CLAVE_APERTURA_FECHA  = "caja.apertura_fecha";
    private static final String CLAVE_APERTURA_FONDO  = "caja.apertura_fondo";
    private static final String CLAVE_APERTURA_NOMBRE = "caja.apertura_nombre";
    private static final String CLAVE_APERTURA_CAJERO = "caja.apertura_cajero_id";

    public boolean hayCajaAbierta() {
        String v = configuracionService.obtener(CLAVE_APERTURA_FECHA, "");
        return v != null && !v.isBlank();
    }

    public LocalDateTime obtenerFechaApertura() {
        String v = configuracionService.obtener(CLAVE_APERTURA_FECHA, "");
        return (v == null || v.isBlank()) ? null : LocalDateTime.parse(v);
    }

    public BigDecimal obtenerFondoInicialApertura() {
        String v = configuracionService.obtener(CLAVE_APERTURA_FONDO, "0");
        return new BigDecimal(v.isBlank() ? "0" : v);
    }

    public String obtenerNombreCajaApertura() {
        return configuracionService.obtener(CLAVE_APERTURA_NOMBRE, "Caja Principal");
    }

    @Transactional
    public void abrirCaja(Usuario cajero, String nombreCaja, BigDecimal fondoInicial) {
        if (hayCajaAbierta()) {
            throw new CajaYaAbiertaException(
                    "Ya hay una caja abierta (\"" + obtenerNombreCajaApertura()
                            + "\"). Debes cerrarla antes de abrir una nueva.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        configuracionService.guardar(CLAVE_APERTURA_FECHA, ahora.toString());
        configuracionService.guardar(CLAVE_APERTURA_FONDO,
                (fondoInicial != null ? fondoInicial : BigDecimal.ZERO).toPlainString());
        configuracionService.guardar(CLAVE_APERTURA_NOMBRE,
                nombreCaja != null && !nombreCaja.isBlank() ? nombreCaja : "Caja Principal");
        configuracionService.guardar(CLAVE_APERTURA_CAJERO, String.valueOf(cajero.getIdUsuario()));

        log.info("Caja abierta: {} — Fondo inicial: RD${} — Cajero: {}",
                obtenerNombreCajaApertura(), fondoInicial, cajero.getNombreCompleto());
        auditoriaService.registrar(cajero, "CAJA_ABIERTA", "CierreCaja", null,
                obtenerNombreCajaApertura() + " — Fondo inicial: RD$"
                        + formatoPlano(fondoInicial));
    }

    private String formatoPlano(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).toPlainString();
    }

    public static class CajaYaAbiertaException extends RuntimeException {
        public CajaYaAbiertaException(String msg) { super(msg); }
    }

    public static class CajaNoAbiertaException extends RuntimeException {
        public CajaNoAbiertaException(String msg) { super(msg); }
    }

    // Calcula el cierre sin guardarlo, para mostrarlo como vista previa.
    // Requiere que haya una caja abierta (fondo inicial y apertura reales,
    // fijados por abrirCaja(...) — ya no se inventan al momento de cerrar).
    public CierreCaja calcular(Usuario cajero, BigDecimal gastos, BigDecimal retiros) {

        if (!hayCajaAbierta()) {
            throw new CajaNoAbiertaException(
                    "No hay ninguna caja abierta. Debes abrir la caja primero.");
        }

        LocalDateTime desde = obtenerFechaApertura();
        LocalDateTime hasta = LocalDateTime.now();
        BigDecimal fondo = obtenerFondoInicialApertura();
        String nombreCaja = obtenerNombreCajaApertura();

        List<Venta> ventas = ventaRepository.findEntreFechas(desde, hasta)
                .stream().filter(v -> !v.getAnulada()).toList();

        BigDecimal totalVentas = ventas.stream()
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int numeroVentas = ventas.size();
        BigDecimal ticketPromedio = numeroVentas > 0
                ? totalVentas.divide(BigDecimal.valueOf(numeroVentas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        int productosVendidos = (int) ventas.stream()
                .flatMap(v -> v.getDetalles().stream())
                .mapToLong(VentaProducto::getCantidad).sum();

        BigDecimal montoEfectivo      = sumaPorMetodo(ventas, "EFECTIVO");
        BigDecimal montoTarjeta       = sumaPorMetodo(ventas, "TARJETA");
        BigDecimal montoTransferencia = sumaPorMetodo(ventas, "TRANSFERENCIA");
        BigDecimal montoCredito       = sumaPorMetodo(ventas, "CREDITO");

        List<CreditoPago> pagosCred = creditoPagoRepository.findAll().stream()
                .filter(p -> !p.getFechaPago().isBefore(desde)
                        && !p.getFechaPago().isAfter(hasta))
                .toList();
        BigDecimal pagosCreditos = pagosCred.stream()
                .map(CreditoPago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagosCreditosEfectivo = pagosCred.stream()
                .filter(p -> "EFECTIVO".equals(p.getMetodoPago()))
                .map(CreditoPago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CuentaPago> pagosProv = cuentaPagoRepository.findAll().stream()
                .filter(p -> !p.getFechaPago().isBefore(desde)
                        && !p.getFechaPago().isAfter(hasta))
                .toList();
        BigDecimal pagosProveedores = pagosProv.stream()
                .map(CuentaPago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagosProveedoresEfectivo = pagosProv.stream()
                .filter(p -> "EFECTIVO".equals(p.getMetodoPago()))
                .map(CuentaPago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal g = gastos != null ? gastos : BigDecimal.ZERO;
        BigDecimal r = retiros != null ? retiros : BigDecimal.ZERO;

        BigDecimal egresosDevolucionEfectivo = movimientoCajaRepository
                .sumaEgresosDevolucionEfectivo(desde, hasta).abs();

        BigDecimal efectivoEsperado = fondo
                .add(montoEfectivo)
                .add(pagosCreditosEfectivo)
                .subtract(pagosProveedoresEfectivo)
                .subtract(egresosDevolucionEfectivo)
                .subtract(g)
                .subtract(r);

        CierreCaja c = new CierreCaja();
        c.setNombreCaja(nombreCaja != null && !nombreCaja.isBlank()
                ? nombreCaja : "Caja Principal");
        c.setCajero(cajero);
        c.setFechaApertura(desde);
        c.setFechaCierre(hasta);
        c.setTotalVentas(totalVentas);
        c.setNumeroVentas(numeroVentas);
        c.setTicketPromedio(ticketPromedio);
        c.setProductosVendidos(productosVendidos);
        c.setMontoEfectivo(montoEfectivo);
        c.setMontoTarjeta(montoTarjeta);
        c.setMontoTransferencia(montoTransferencia);
        c.setMontoCredito(montoCredito);
        c.setFondoInicial(fondo);
        c.setPagosCreditos(pagosCreditos);
        c.setPagosProveedores(pagosProveedores);
        c.setGastos(g);
        c.setRetiros(r);
        c.setEfectivoEsperado(efectivoEsperado);
        c.setTotalPorCobrarPendiente(dashboardService.getTotalCreditosPendientes());
        c.setTotalPorPagarPendiente(dashboardService.getTotalCuentasPorPagar());
        return c;
    }

    @Transactional
    public CierreCaja confirmar(CierreCaja calculado, BigDecimal efectivoContado,
                                String observacion) {
        BigDecimal contado = efectivoContado != null ? efectivoContado : BigDecimal.ZERO;
        BigDecimal diferencia = contado.subtract(calculado.getEfectivoEsperado());

        if (diferencia.compareTo(BigDecimal.ZERO) != 0
                && (observacion == null || observacion.isBlank())) {
            throw new ObservacionRequeridaException(
                    "Hay una diferencia de RD$" + diferencia.abs().toPlainString()
                            + " — debes explicarla en las observaciones antes de cerrar.");
        }

        calculado.setEfectivoContado(contado);
        calculado.setDiferencia(diferencia);
        calculado.setEstado(
                diferencia.compareTo(BigDecimal.ZERO) == 0 ? "EXACTO"
                        : diferencia.compareTo(BigDecimal.ZERO) > 0 ? "SOBRANTE" : "FALTANTE");
        calculado.setObservacion(observacion);

        CierreCaja guardado = cierreCajaRepository.save(calculado);
        log.info("Cierre de caja #{} — {} — Esperado: {} — Contado: {} — Diferencia: {}",
                guardado.getIdCierre(), guardado.getEstado(),
                guardado.getEfectivoEsperado(), guardado.getEfectivoContado(),
                guardado.getDiferencia());
        auditoriaService.registrar(guardado.getCajero(), "CIERRE_CAJA", "CierreCaja",
                guardado.getIdCierre(),
                guardado.getNombreCaja() + " — " + guardado.getEstado()
                        + " (RD$" + guardado.getDiferencia().toPlainString() + ")");

        configuracionService.guardar(CLAVE_APERTURA_FECHA, "");
        configuracionService.guardar(CLAVE_APERTURA_FONDO, "");
        configuracionService.guardar(CLAVE_APERTURA_NOMBRE, "");
        configuracionService.guardar(CLAVE_APERTURA_CAJERO, "");

        return guardado;
    }

    public List<CierreCaja> obtenerTodos() {
        return cierreCajaRepository.findTodos();
    }

    private BigDecimal sumaPorMetodo(List<Venta> ventas, String metodo) {
        return ventas.stream()
                .filter(v -> metodo.equals(v.getMetodoPago()))
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static class ObservacionRequeridaException extends RuntimeException {
        public ObservacionRequeridaException(String msg) { super(msg); }
    }

    // ================================================================
    // PDF
    // ================================================================

    private static final Color COLOR_PRIMARIO   = new Color(37, 99, 235);
    private static final Color COLOR_ENCABEZADO = new Color(239, 246, 255);
    private static final Color COLOR_BORDE      = new Color(191, 219, 254);
    private static final Color COLOR_TEXTO_SEC  = new Color(100, 116, 139);
    private static final Color COLOR_EXACTO     = new Color(21, 128, 61);
    private static final Color COLOR_SOBRANTE   = new Color(180, 83, 9);
    private static final Color COLOR_FALTANTE   = new Color(220, 38, 38);

    private static final Font F_TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, COLOR_PRIMARIO);
    private static final Font F_SUBTITULO = new Font(Font.HELVETICA, 11, Font.NORMAL, COLOR_TEXTO_SEC);
    private static final Font F_SECCION = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(15, 23, 42));
    private static final Font F_HEADER = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(30, 64, 175));
    private static final Font F_NORMAL = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font F_BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font F_SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_TEXTO_SEC);

    public String generarPdf(CierreCaja c) {
        String ruta = System.getProperty("java.io.tmpdir") + java.io.File.separator
                + "cierre_caja_" + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf";
        try {
            Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
            PdfWriter.getInstance(doc, new FileOutputStream(ruta));
            doc.open();

            String negocio = configuracionService.obtener(
                    "negocio.nombre", "Jordis Technology");
            doc.add(new Paragraph(negocio, F_TITULO));
            doc.add(new Paragraph("Cierre de Caja", F_SECCION));
            doc.add(new Paragraph(c.getNombreCaja() + "  ·  Cajero: "
                    + c.getCajero().getNombreCompleto(), F_SUBTITULO));
            doc.add(new Paragraph("Apertura: " + c.getFechaApertura().format(FMT)
                    + "   Cierre: " + c.getFechaCierre().format(FMT), F_SMALL));
            doc.add(new Paragraph(" "));
            doc.add(new Chunk(new LineSeparator(1f, 100f, COLOR_BORDE, Element.ALIGN_CENTER, -2)));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Resumen de ventas", F_SECCION));
            PdfPTable kpis = new PdfPTable(4);
            kpis.setWidthPercentage(100);
            kpis.setSpacingBefore(6);
            kpis.setSpacingAfter(14);
            agregarKpi(kpis, "TOTAL VENDIDO", "RD$" + fmt(c.getTotalVentas()));
            agregarKpi(kpis, "VENTAS", String.valueOf(c.getNumeroVentas()));
            agregarKpi(kpis, "TICKET PROMEDIO", "RD$" + fmt(c.getTicketPromedio()));
            agregarKpi(kpis, "PRODUCTOS VENDIDOS", String.valueOf(c.getProductosVendidos()));
            doc.add(kpis);

            doc.add(new Paragraph("Desglose por método de pago", F_SECCION));
            PdfPTable tMetodos = tabla(new float[]{2f, 1f});
            encabezado(tMetodos, "Método", "Monto");
            fila(tMetodos, "Efectivo", "RD$" + fmt(c.getMontoEfectivo()));
            fila(tMetodos, "Tarjeta", "RD$" + fmt(c.getMontoTarjeta()));
            fila(tMetodos, "Transferencia", "RD$" + fmt(c.getMontoTransferencia()));
            fila(tMetodos, "Crédito", "RD$" + fmt(c.getMontoCredito()));
            doc.add(tMetodos);

            doc.add(new Paragraph("Movimientos de efectivo", F_SECCION));
            PdfPTable tMov = tabla(new float[]{2f, 1f});
            fila(tMov, "Fondo inicial", "RD$" + fmt(c.getFondoInicial()));
            fila(tMov, "Ventas en efectivo", "RD$" + fmt(c.getMontoEfectivo()));
            fila(tMov, "Pagos de créditos recibidos", "RD$" + fmt(c.getPagosCreditos()));
            fila(tMov, "Pagos a proveedores", "- RD$" + fmt(c.getPagosProveedores()));
            fila(tMov, "Gastos", "- RD$" + fmt(c.getGastos()));
            fila(tMov, "Retiros parciales", "- RD$" + fmt(c.getRetiros()));
            filaBold(tMov, "Efectivo esperado", "RD$" + fmt(c.getEfectivoEsperado()));
            doc.add(tMov);

            doc.add(new Paragraph("Conteo físico", F_SECCION));
            PdfPTable tConteo = tabla(new float[]{2f, 1f});
            fila(tConteo, "Efectivo contado", "RD$" + fmt(c.getEfectivoContado()));
            fila(tConteo, "Efectivo esperado", "RD$" + fmt(c.getEfectivoEsperado()));
            doc.add(tConteo);

            Color colorEstado = "EXACTO".equals(c.getEstado()) ? COLOR_EXACTO
                    : "SOBRANTE".equals(c.getEstado()) ? COLOR_SOBRANTE : COLOR_FALTANTE;
            Color fondoEstado = "EXACTO".equals(c.getEstado()) ? new Color(240, 253, 244)
                    : "SOBRANTE".equals(c.getEstado()) ? new Color(255, 251, 235) : new Color(254, 242, 242);

            PdfPTable tResultado = new PdfPTable(1);
            tResultado.setWidthPercentage(100);
            tResultado.setSpacingBefore(10);
            tResultado.setSpacingAfter(14);
            PdfPCell celdaResultado = new PdfPCell(new Phrase(
                    etiquetaEstado(c.getEstado()) + "  —  RD$" + fmt(c.getDiferencia().abs()),
                    new Font(Font.HELVETICA, 14, Font.BOLD, colorEstado)));
            celdaResultado.setBackgroundColor(fondoEstado);
            celdaResultado.setBorderColor(colorEstado);
            celdaResultado.setPadding(12);
            celdaResultado.setHorizontalAlignment(Element.ALIGN_CENTER);
            tResultado.addCell(celdaResultado);
            doc.add(tResultado);

            if (c.getObservacion() != null && !c.getObservacion().isBlank()) {
                doc.add(new Paragraph("Observaciones", F_SECCION));
                doc.add(new Paragraph(c.getObservacion(), F_NORMAL));
                doc.add(new Paragraph(" "));
            }

            doc.add(new Paragraph("Información adicional (solo informativo)", F_SECCION));
            PdfPTable tInfo = tabla(new float[]{2f, 1f});
            fila(tInfo, "Total por cobrar pendiente", "RD$" + fmt(c.getTotalPorCobrarPendiente()));
            fila(tInfo, "Total por pagar pendiente", "RD$" + fmt(c.getTotalPorPagarPendiente()));
            doc.add(tInfo);

            doc.add(new Paragraph(" "));
            doc.add(new Chunk(new LineSeparator(0.5f, 100f, COLOR_BORDE, Element.ALIGN_CENTER, -2)));
            Paragraph pie = new Paragraph(
                    "Cierre de caja generado el " + LocalDateTime.now().format(FMT)
                            + "  ·  " + negocio, F_SMALL);
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(4);
            doc.add(pie);

            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando el PDF del cierre de caja: "
                    + e.getMessage(), e);
        }
    }

    private String etiquetaEstado(String estado) {
        return switch (estado) {
            case "EXACTO"   -> "✔ CAJA EXACTA";
            case "SOBRANTE" -> "▲ SOBRANTE";
            default          -> "▼ FALTANTE";
        };
    }

    private void agregarKpi(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(COLOR_BORDE);
        cell.setBackgroundColor(COLOR_ENCABEZADO);
        cell.setPadding(8);
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + "\n", F_SMALL));
        p.add(new Chunk(valor, new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_PRIMARIO)));
        cell.addElement(p);
        tabla.addCell(cell);
    }

    private PdfPTable tabla(float[] anchos) {
        PdfPTable t = new PdfPTable(anchos.length);
        t.setWidthPercentage(100);
        t.setWidths(anchos);
        t.setSpacingBefore(6);
        t.setSpacingAfter(14);
        return t;
    }

    private void encabezado(PdfPTable t, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, F_HEADER));
            c.setBackgroundColor(COLOR_ENCABEZADO);
            c.setPadding(5);
            c.setBorderColor(COLOR_BORDE);
            t.addCell(c);
        }
    }

    private void fila(PdfPTable t, String etiqueta, String valor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, F_NORMAL));
        c1.setPadding(5); c1.setBorderColor(COLOR_BORDE);
        PdfPCell c2 = new PdfPCell(new Phrase(valor, F_NORMAL));
        c2.setPadding(5); c2.setBorderColor(COLOR_BORDE);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c1); t.addCell(c2);
    }

    private void filaBold(PdfPTable t, String etiqueta, String valor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, F_BOLD));
        c1.setPadding(6); c1.setBorderColor(COLOR_BORDE);
        c1.setBackgroundColor(new Color(248, 250, 252));
        PdfPCell c2 = new PdfPCell(new Phrase(valor,
                new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_PRIMARIO)));
        c2.setPadding(6); c2.setBorderColor(COLOR_BORDE);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setBackgroundColor(new Color(248, 250, 252));
        t.addCell(c1); t.addCell(c2);
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }
}