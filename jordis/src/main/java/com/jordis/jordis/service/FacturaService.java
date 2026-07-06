package com.jordis.jordis.service;

import com.jordis.jordis.model.Venta;
import com.jordis.jordis.model.VentaGarantia;
import com.jordis.jordis.model.VentaProducto;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

// Usamos la librería OpenPDF que ya viene con JasperReports
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class FacturaService {

    private final ConfiguracionService configuracionService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font FONT_TITULO =
            new Font(Font.HELVETICA, 16, Font.BOLD, new Color(37, 99, 235));
    private static final Font FONT_SUBTITULO =
            new Font(Font.HELVETICA, 11, Font.BOLD, new Color(30, 64, 175));
    private static final Font FONT_NORMAL =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FONT_BOLD =
            new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font FONT_SMALL =
            new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(100, 116, 139));
    private static final Font FONT_TOTAL =
            new Font(Font.HELVETICA, 12, Font.BOLD, new Color(37, 99, 235));
    private String cfg(String clave, String defecto) {
        return configuracionService.obtener(clave, defecto);
    }

    /**
     * Genera la factura en PDF y la guarda en un archivo temporal.
     * Retorna la ruta del archivo generado.
     */
    public String generarFactura(Venta venta) {
        try {
            String rutaArchivo = System.getProperty("java.io.tmpdir")
                    + File.separator + "factura_" + venta.getNumeroFactura() + ".pdf";

            Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(doc, new FileOutputStream(rutaArchivo));
            doc.open();

            agregarEncabezado(doc, venta);
            agregarDatosCliente(doc, venta);
            agregarTablaProductos(doc, venta);
            agregarTotales(doc, venta);
            agregarGarantias(doc, venta);
            agregarPieCredito(doc, venta);
            agregarPiePagina(doc, venta);

            doc.close();
            log.info("Factura generada: {}", rutaArchivo);
            return rutaArchivo;

        } catch (Exception e) {
            log.error("Error generando factura PDF", e);
            throw new RuntimeException("Error al generar la factura: " + e.getMessage());
        }
    }

    private void agregarEncabezado(Document doc, Venta venta) throws DocumentException {

        // Datos configurables del negocio
        String nombreEmpresa = configuracionService.obtener(
                "negocio.nombre", "Jordis Technology");

        String rnc = configuracionService.obtener(
                "negocio.rnc", "131-00000-0");

        String telefono = configuracionService.obtener(
                "negocio.telefono", "809-000-0000");

        String direccion = configuracionService.obtener(
                "negocio.direccion", "Santo Domingo, RD");

        String email = configuracionService.obtener(
                "negocio.email", "info@jordis.com");

        // ==========================
        // Encabezado de la empresa
        // ==========================

        Paragraph titulo = new Paragraph(nombreEmpresa, FONT_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        if (!rnc.isBlank()) {
            Paragraph p = new Paragraph("RNC: " + rnc, FONT_SMALL);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        if (!direccion.isBlank()) {
            Paragraph p = new Paragraph(direccion, FONT_SMALL);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        if (!telefono.isBlank()) {
            Paragraph p = new Paragraph("Tel: " + telefono, FONT_SMALL);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        if (!email.isBlank()) {
            Paragraph p = new Paragraph(email, FONT_SMALL);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        doc.add(new Paragraph(" "));

        // Separador
        LineSeparator linea = new LineSeparator(
                1f,
                100f,
                new Color(219, 234, 254),
                Element.ALIGN_CENTER,
                -2);

        doc.add(new Chunk(linea));
        doc.add(new Paragraph(" "));

        // Número de factura
        Paragraph numFac = new Paragraph(
                "FACTURA " + venta.getNumeroFactura(),
                FONT_SUBTITULO);

        numFac.setAlignment(Element.ALIGN_CENTER);
        doc.add(numFac);

        // Mostrar NCF si existe
        if (venta.getNcf() != null && !venta.getNcf().isBlank()) {
            Paragraph ncf = new Paragraph(
                    "NCF: " + venta.getNcf(),
                    FONT_BOLD);

            ncf.setAlignment(Element.ALIGN_CENTER);
            doc.add(ncf);
        }

        // Venta a crédito
        if (Boolean.TRUE.equals(venta.getEsCredito())) {

            Paragraph credBadge = new Paragraph(
                    "[ VENTA A CRÉDITO ]",
                    new Font(
                            Font.HELVETICA,
                            10,
                            Font.BOLD,
                            new Color(220, 38, 38)));

            credBadge.setAlignment(Element.ALIGN_CENTER);
            doc.add(credBadge);
        }

        doc.add(new Paragraph(" "));

        // Información general
        PdfPTable infoTabla = new PdfPTable(2);
        infoTabla.setWidthPercentage(100);
        infoTabla.setSpacingAfter(10);

        agregarCeldaInfo(
                infoTabla,
                "Fecha:",
                venta.getFechaHora().format(FMT));

        agregarCeldaInfo(
                infoTabla,
                "Atendido por:",
                venta.getCajero().getNombreCompleto());

        agregarCeldaInfo(
                infoTabla,
                "Método de pago:",
                venta.getMetodoPago());

        doc.add(infoTabla);
    }

    private void agregarDatosCliente(Document doc, Venta venta) throws DocumentException {
        Paragraph titulo = new Paragraph("DATOS DEL CLIENTE", FONT_SUBTITULO);
        titulo.setSpacingBefore(6);
        doc.add(titulo);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));

        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);
        tabla.setSpacingAfter(10);

        if (venta.getCliente() != null) {
            var c = venta.getCliente();
            agregarCeldaInfo(tabla, "Cliente:", c.getNombreCompleto());
            if (c.esEmpresa()) {
                agregarCeldaInfo(tabla, "RNC:", c.getRnc() != null ? c.getRnc() : "—");
                agregarCeldaInfo(tabla, "Contacto:",
                        c.getContactoPrincipal() != null ? c.getContactoPrincipal() : "—");
            } else {
                agregarCeldaInfo(tabla, "Cédula:",
                        c.getCedulaIdentificacion() != null
                                ? c.getCedulaIdentificacion() : "—");
            }
            agregarCeldaInfo(tabla, "Teléfono:",
                    c.getTelefono() != null ? c.getTelefono() : "—");
        } else {
            agregarCeldaInfo(tabla, "Cliente:", "Consumidor final");
        }

        agregarCeldaInfo(tabla, "Método de pago:", venta.getMetodoPago());
        doc.add(tabla);
    }

    private void agregarTablaProductos(Document doc, Venta venta) throws DocumentException {
        Paragraph titulo = new Paragraph("DETALLE DE PRODUCTOS", FONT_SUBTITULO);
        titulo.setSpacingBefore(4);
        doc.add(titulo);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));

        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{4f, 1.5f, 2f, 1.5f, 2f});
        tabla.setSpacingAfter(8);

        // Encabezados
        Color colorEncabezado = new Color(239, 246, 255);
        Font fontEncabezado = new Font(Font.HELVETICA, 9, Font.BOLD,
                new Color(30, 64, 175));
        agregarEncabezadoTabla(tabla, "Producto",     colorEncabezado, fontEncabezado);
        agregarEncabezadoTabla(tabla, "Cant.",        colorEncabezado, fontEncabezado);
        agregarEncabezadoTabla(tabla, "Precio unit.", colorEncabezado, fontEncabezado);
        agregarEncabezadoTabla(tabla, "Garantía",     colorEncabezado, fontEncabezado);
        agregarEncabezadoTabla(tabla, "Subtotal",     colorEncabezado, fontEncabezado);

        // Filas de productos
        for (VentaProducto vp : venta.getDetalles()) {
            // Buscar garantía del producto
            String garantiaTexto = venta.getGarantias().stream()
                    .filter(g -> g.getProducto().getIdProducto()
                            .equals(vp.getProducto().getIdProducto()))
                    .map(g -> g.getMeses() > 0 ? g.getMeses() + " meses" : g.getDescripcion())
                    .findFirst().orElse("—");

            agregarCeldaTabla(tabla, vp.getProducto().getNombre(), Element.ALIGN_LEFT);
            agregarCeldaTabla(tabla, String.valueOf(vp.getCantidad()), Element.ALIGN_CENTER);
            agregarCeldaTabla(tabla,
                    "RD$" + vp.getPrecioUnitario().toPlainString(), Element.ALIGN_RIGHT);
            agregarCeldaTabla(tabla, garantiaTexto, Element.ALIGN_CENTER);
            agregarCeldaTabla(tabla,
                    "RD$" + vp.getSubtotal().toPlainString(), Element.ALIGN_RIGHT);
        }

        doc.add(tabla);
    }

    private void agregarTotales(Document doc, Venta venta) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(50);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingAfter(10);

        // Subtotal
        agregarFilaTotal(tabla,
                "Subtotal:", "RD$" + venta.getSubtotal().toPlainString(),
                FONT_NORMAL, FONT_NORMAL);

        // Descuento
        BigDecimal desc = venta.getDescuentoPorcentual();
        if (desc != null && desc.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montoDesc = venta.getSubtotal()
                    .multiply(desc)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            agregarFilaTotal(tabla,
                    "Descuento (" + desc.toPlainString() + "%):",
                    "- RD$" + montoDesc.toPlainString(),
                    new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(220, 38, 38)),
                    new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(220, 38, 38)));
        }

        // ITBIS
        if (venta.getMontoItbis() != null
                && venta.getMontoItbis().compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotal(tabla,
                    "ITBIS (" + venta.getItbisPorcentual().toPlainString() + "%):",
                    "+ RD$" + venta.getMontoItbis().toPlainString(),
                    new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 116, 139)),
                    new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 116, 139)));
        }

        // Total
        agregarFilaTotal(tabla,
                "TOTAL:", "RD$" + venta.getTotal().toPlainString(),
                FONT_TOTAL, FONT_TOTAL);

        // Crédito
        if (venta.getEsCredito()) {
            BigDecimal pagado = venta.getTotalPagado();
            BigDecimal saldo  = venta.getSaldoPendiente();
            agregarFilaTotal(tabla,
                    "Pagado:", "RD$" + pagado.toPlainString(),
                    FONT_BOLD, FONT_BOLD);
            Font fontSaldo = saldo.compareTo(BigDecimal.ZERO) > 0
                    ? new Font(Font.HELVETICA, 10, Font.BOLD, new Color(220, 38, 38))
                    : new Font(Font.HELVETICA, 10, Font.BOLD, new Color(21, 128, 61));
            agregarFilaTotal(tabla,
                    "Saldo pendiente:", "RD$" + saldo.toPlainString(),
                    fontSaldo, fontSaldo);
        }

        doc.add(tabla);

        // NCF — si aplica, mostrar después de los totales
        if (venta.getNcf() != null) {
            doc.add(new Paragraph(" "));
            PdfPTable tablaNCF = new PdfPTable(1);
            tablaNCF.setWidthPercentage(100);

            PdfPCell celdaNCF = new PdfPCell();
            celdaNCF.setBackgroundColor(new Color(240, 253, 244));
            celdaNCF.setBorderColor(new Color(187, 247, 208));
            celdaNCF.setPadding(8);

            Paragraph pNCF = new Paragraph();
            pNCF.add(new Chunk("Comprobante Fiscal (NCF): ",
                    new Font(Font.HELVETICA, 10, Font.BOLD,
                            new Color(21, 128, 61))));
            pNCF.add(new Chunk(venta.getNcf(),
                    new Font(Font.HELVETICA, 11, Font.BOLD,
                            new Color(21, 128, 61))));

            if (venta.getTipoNcf() != null) {
                pNCF.add(new Chunk("\nTipo: " + venta.getTipoNcf()
                        + switch (venta.getTipoNcf()) {
                    case "B01" -> " — Crédito Fiscal";
                    case "B02" -> " — Consumidor Final";
                    case "B14" -> " — Régimen Especial";
                    case "B15" -> " — Gubernamental";
                    default    -> "";
                },
                        new Font(Font.HELVETICA, 9, Font.NORMAL,
                                new Color(100, 116, 139))));
            }

            celdaNCF.setPhrase(new Phrase(pNCF.getContent()));
            celdaNCF.addElement(pNCF);
            tablaNCF.addCell(celdaNCF);
            doc.add(tablaNCF);
        }
    }

    private void agregarGarantias(Document doc, Venta venta) throws DocumentException {
        if (venta.getGarantias().isEmpty()) return;

        Paragraph titulo = new Paragraph("GARANTÍAS", FONT_SUBTITULO);
        titulo.setSpacingBefore(4);
        doc.add(titulo);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));

        for (VentaGarantia g : venta.getGarantias()) {
            String texto = "• " + g.getProducto().getNombre() + ": "
                    + g.getDescripcion()
                    + (g.getMeses() > 0 ? " (" + g.getMeses() + " meses)" : "")
                    + (g.getFechaVence() != null
                    ? " — Vence: " + g.getFechaVence().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
            doc.add(new Paragraph(texto, FONT_SMALL));
        }
        doc.add(new Paragraph(" "));
    }

    private void agregarPieCredito(Document doc, Venta venta) throws DocumentException {
        if (!venta.getEsCredito()) return;

        Paragraph titulo = new Paragraph("CONDICIONES DE CRÉDITO", FONT_SUBTITULO);
        titulo.setSpacingBefore(4);
        doc.add(titulo);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                new Color(254, 226, 226), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));

        if (venta.getFechaLimiteCredito() != null) {
            Paragraph limite = new Paragraph(
                    "Fecha límite de pago: "
                            + venta.getFechaLimiteCredito().format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    new Font(Font.HELVETICA, 10, Font.BOLD, new Color(220, 38, 38)));
            doc.add(limite);
        }

        String empresa = cfg("negocio.nombre", "Jordis Technology");

        doc.add(new Paragraph(
                "El incumplimiento en el pago en la fecha acordada puede conllevar "
                        + "acciones de cobro por parte de " + empresa + ".",
                FONT_SMALL));
    }

    private void agregarPiePagina(Document doc, Venta venta) throws DocumentException {
        doc.add(new Chunk(new LineSeparator(1f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));

        if (venta.getNotas() != null && !venta.getNotas().isBlank()) {
            Paragraph notas = new Paragraph("Notas: " + venta.getNotas(), FONT_SMALL);
            doc.add(notas);
            doc.add(new Paragraph(" "));
        }

        String nombreEmpresa = cfg("negocio.nombre", "Jordis Technology");
        String pie = cfg("factura.pie", "¡Gracias por su compra!");

        Paragraph gracias = new Paragraph(
                pie + " — " + nombreEmpresa,
                FONT_SMALL);
        gracias.setAlignment(Element.ALIGN_CENTER);
        doc.add(gracias);

        Paragraph facNum = new Paragraph(
                "Factura " + venta.getNumeroFactura()
                        + " | Emitida el " + venta.getFechaHora().format(FMT), FONT_SMALL);
        facNum.setAlignment(Element.ALIGN_CENTER);
        doc.add(facNum);
    }

    // ---- Helpers ----

    private void agregarCeldaInfo(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell cEtiqueta = new PdfPCell(new Phrase(etiqueta, FONT_BOLD));
        cEtiqueta.setBorder(Rectangle.NO_BORDER);
        cEtiqueta.setPadding(2);
        tabla.addCell(cEtiqueta);

        PdfPCell cValor = new PdfPCell(new Phrase(valor, FONT_NORMAL));
        cValor.setBorder(Rectangle.NO_BORDER);
        cValor.setPadding(2);
        tabla.addCell(cValor);
    }

    private void agregarEncabezadoTabla(PdfPTable tabla, String texto,
                                        Color fondo, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(fondo);
        cell.setPadding(5);
        cell.setBorderColor(new Color(219, 234, 254));
        tabla.addCell(cell);
    }

    private void agregarCeldaTabla(PdfPTable tabla, String texto, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(219, 234, 254));
        tabla.addCell(cell);
    }

    private void agregarFilaTotal(PdfPTable tabla,
                                  String etiqueta, String valor,
                                  Font fontEtiqueta, Font fontValor) {
        PdfPCell cEtiqueta = new PdfPCell(new Phrase(etiqueta, fontEtiqueta));
        cEtiqueta.setBorder(Rectangle.NO_BORDER);
        cEtiqueta.setPadding(3);
        cEtiqueta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(cEtiqueta);

        PdfPCell cValor = new PdfPCell(new Phrase(valor, fontValor));
        cValor.setBorder(Rectangle.NO_BORDER);
        cValor.setPadding(3);
        cValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(cValor);
    }

    /**
     * Abre el PDF generado con el visor predeterminado del sistema.
     */
    public void abrirPDF(String rutaArchivo) {
        File archivo = new File(rutaArchivo);

        if (!archivo.exists()) {
            throw new RuntimeException("El archivo PDF no existe: " + rutaArchivo);
        }

        try {
            // Forzar que AWT no sea headless antes de usar Desktop
            System.setProperty("java.awt.headless", "false");

            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(archivo);
                return;
            }
        } catch (Exception e) {
            log.warn("Desktop.open falló, intentando con comando del sistema: {}",
                    e.getMessage());
        }

        // Fallback: usar el comando nativo de Windows para abrir el archivo
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "\"\"",
                        archivo.getAbsolutePath());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", archivo.getAbsolutePath());
            } else {
                pb = new ProcessBuilder("xdg-open", archivo.getAbsolutePath());
            }

            pb.start();
            log.info("PDF abierto vía comando del sistema: {}", rutaArchivo);

        } catch (Exception e) {
            log.error("No se pudo abrir el PDF por ningún método", e);
            throw new RuntimeException(
                    "No se pudo abrir el PDF automáticamente. "
                            + "El archivo se guardó en: " + rutaArchivo);
        }
    }
}