package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// OpenPDF imports
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteService {

    private final VentaRepository        ventaRepository;
    private final ProductoRepository     productoRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    // ================================================================
    // REPORTE 1: Ventas por período
    // ================================================================

    public List<Venta> obtenerVentasPorPeriodo(LocalDateTime desde,
                                               LocalDateTime hasta) {
        return ventaRepository.findEntreFechas(desde, hasta);
    }

    public String exportarVentasPDF(List<Venta> ventas,
                                    LocalDateTime desde,
                                    LocalDateTime hasta) {
        String ruta = tmpPath("reporte_ventas_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 40, 40);
            PdfWriter.getInstance(doc, new FileOutputStream(ruta));
            doc.open();

            agregarTituloPDF(doc,
                    "Reporte de Ventas",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA));

            // Tabla de ventas
            PdfPTable tabla = new PdfPTable(7);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{2f, 3f, 3f, 4f, 2.5f, 2f, 2f});
            tabla.setSpacingBefore(10);

            String[] headers = {"Factura", "Fecha", "Cliente",
                    "Productos", "Total", "Descuento", "Pago"};
            for (String h : headers) {
                agregarEncabezadoPDF(tabla, h);
            }

            BigDecimal grandTotal = BigDecimal.ZERO;
            for (Venta v : ventas) {
                String productos = v.getDetalles().stream()
                        .map(vp -> vp.getProducto().getNombre()
                                + " x" + vp.getCantidad())
                        .reduce((a, b) -> a + ", " + b).orElse("—");

                agregarCeldaPDF(tabla, nvl(v.getNumeroFactura()),
                        Element.ALIGN_CENTER);
                agregarCeldaPDF(tabla, v.getFechaHora().format(FMT),
                        Element.ALIGN_CENTER);
                agregarCeldaPDF(tabla,
                        v.getCliente() != null
                                ? v.getCliente().getNombreCompleto() : "Ocasional",
                        Element.ALIGN_LEFT);
                agregarCeldaPDF(tabla, productos, Element.ALIGN_LEFT);
                agregarCeldaPDF(tabla,
                        "RD$" + v.getTotal().toPlainString(),
                        Element.ALIGN_RIGHT);
                agregarCeldaPDF(tabla,
                        v.getDescuentoPorcentual().compareTo(BigDecimal.ZERO) > 0
                                ? v.getDescuentoPorcentual().toPlainString() + "%" : "—",
                        Element.ALIGN_CENTER);
                agregarCeldaPDF(tabla, v.getMetodoPago(),
                        Element.ALIGN_CENTER);

                grandTotal = grandTotal.add(v.getTotal());
            }

            doc.add(tabla);

            // Fila de total
            Paragraph total = new Paragraph(
                    "\nTotal del período: RD$" + grandTotal.toPlainString(),
                    new Font(Font.HELVETICA, 12, Font.BOLD,
                            new Color(37, 99, 235)));
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

            agregarPiePDF(doc, ventas.size() + " ventas en el período");
            doc.close();
            log.info("Reporte ventas PDF generado: {}", ruta);
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }


    // ================================================================
    // REPORTE 2: Inventario actual
    // ================================================================

    public List<Producto> obtenerInventario() {
        return productoRepository.findByActivoTrue();
    }

    public String exportarInventarioPDF(List<Producto> productos) {
        String ruta = tmpPath("reporte_inventario_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 40, 40);
            PdfWriter.getInstance(doc, new FileOutputStream(ruta));
            doc.open();

            agregarTituloPDF(doc, "Reporte de Inventario",
                    "Generado: " + LocalDateTime.now().format(FMT));

            PdfPTable tabla = new PdfPTable(7);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{3f, 2f, 2f, 1.5f, 1.5f, 2.5f, 2.5f});
            tabla.setSpacingBefore(10);

            String[] headers = {"Producto", "Marca", "Categoría",
                    "Stock", "Mínimo", "Precio venta", "Precio sugerido"};
            for (String h : headers) agregarEncabezadoPDF(tabla, h);

            for (Producto p : productos) {
                boolean bajo = p.isStockBajo();
                Color colorFila = bajo
                        ? new Color(254, 243, 199) : Color.WHITE;

                agregarCeldaColorPDF(tabla,
                        p.getNombre(), Element.ALIGN_LEFT, colorFila);
                agregarCeldaColorPDF(tabla,
                        nvl(p.getMarca()), Element.ALIGN_LEFT, colorFila);
                agregarCeldaColorPDF(tabla,
                        p.getCategoria() != null
                                ? p.getCategoria().getNombre() : "—",
                        Element.ALIGN_CENTER, colorFila);

                Font fontStock = bajo
                        ? new Font(Font.HELVETICA, 9, Font.BOLD,
                        new Color(180, 83, 9))
                        : new Font(Font.HELVETICA, 9, Font.NORMAL,
                        new Color(21, 128, 61));
                PdfPCell cStock = new PdfPCell(
                        new Phrase(String.valueOf(p.getStock()), fontStock));
                cStock.setPadding(4);
                cStock.setHorizontalAlignment(Element.ALIGN_CENTER);
                cStock.setBackgroundColor(colorFila);
                cStock.setBorderColor(new Color(219, 234, 254));
                tabla.addCell(cStock);

                agregarCeldaColorPDF(tabla,
                        String.valueOf(p.getStockMinimo()),
                        Element.ALIGN_CENTER, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + p.getPrecioUnitario().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        p.getPrecioSugerido() != null
                                ? "RD$" + p.getPrecioSugerido().toPlainString() : "—",
                        Element.ALIGN_RIGHT, colorFila);
            }

            doc.add(tabla);
            long bajos = productos.stream()
                    .filter(Producto::isStockBajo).count();
            Paragraph nota = new Paragraph(
                    "\n" + bajos + " producto(s) con stock bajo (fondo amarillo)",
                    new Font(Font.HELVETICA, 10, Font.ITALIC,
                            new Color(180, 83, 9)));
            doc.add(nota);

            agregarPiePDF(doc, productos.size() + " productos activos");
            doc.close();
            log.info("Reporte inventario PDF generado: {}", ruta);
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }


    // ================================================================
    // REPORTE 3: Cuentas por cobrar
    // ================================================================

    public List<Venta> obtenerCuentasPorCobrar() {
        return ventaRepository.findCreditos();
    }

    public String exportarCobrarPDF(List<Venta> creditos) {
        String ruta = tmpPath("reporte_cobrar_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 40, 40);
            PdfWriter.getInstance(doc, new FileOutputStream(ruta));
            doc.open();

            agregarTituloPDF(doc, "Cuentas por Cobrar",
                    "Generado: " + LocalDateTime.now().format(FMT));

            PdfPTable tabla = new PdfPTable(6);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{2f, 3f, 2.5f, 2.5f, 2.5f, 2.5f});
            tabla.setSpacingBefore(10);

            String[] headers = {"Factura", "Cliente", "Total",
                    "Pagado", "Saldo", "Vencimiento"};
            for (String h : headers) agregarEncabezadoPDF(tabla, h);

            BigDecimal totalSaldo = BigDecimal.ZERO;
            for (Venta v : creditos) {
                boolean vencido = v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito().isBefore(LocalDateTime.now())
                        && !v.estaCancelado();
                Color colorFila = vencido
                        ? new Color(254, 226, 226) : Color.WHITE;

                agregarCeldaColorPDF(tabla,
                        nvl(v.getNumeroFactura()),
                        Element.ALIGN_CENTER, colorFila);
                agregarCeldaColorPDF(tabla,
                        v.getCliente() != null
                                ? v.getCliente().getNombreCompleto() : "—",
                        Element.ALIGN_LEFT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + v.getTotal().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + v.getTotalPagado().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + v.getSaldoPendiente().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        v.getFechaLimiteCredito() != null
                                ? v.getFechaLimiteCredito().format(FMT_FECHA) : "—",
                        Element.ALIGN_CENTER, colorFila);

                totalSaldo = totalSaldo.add(v.getSaldoPendiente());
            }

            doc.add(tabla);

            Paragraph total = new Paragraph(
                    "\nTotal saldo por cobrar: RD$" + totalSaldo.toPlainString(),
                    new Font(Font.HELVETICA, 12, Font.BOLD,
                            new Color(220, 38, 38)));
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

            agregarPiePDF(doc, creditos.size() + " créditos");
            doc.close();
            log.info("Reporte cobrar PDF generado: {}", ruta);
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }


    // ================================================================
    // REPORTE 4: Cuentas por pagar
    // ================================================================

    public List<com.jordis.jordis.model.CuentaPorPagar>
    obtenerCuentasPorPagar() {
        return cuentaPorPagarRepository.findTodas();
    }

    public String exportarPagarPDF(
            List<com.jordis.jordis.model.CuentaPorPagar> cuentas) {
        String ruta = tmpPath("reporte_pagar_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 40, 40);
            PdfWriter.getInstance(doc, new FileOutputStream(ruta));
            doc.open();

            agregarTituloPDF(doc, "Cuentas por Pagar",
                    "Generado: " + LocalDateTime.now().format(FMT));

            PdfPTable tabla = new PdfPTable(6);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{1f, 3f, 2.5f, 2.5f, 2.5f, 2.5f});
            tabla.setSpacingBefore(10);

            String[] headers = {"#", "Proveedor", "Total",
                    "Pagado", "Saldo", "Vencimiento"};
            for (String h : headers) agregarEncabezadoPDF(tabla, h);

            BigDecimal totalSaldo = BigDecimal.ZERO;
            for (var c : cuentas) {
                boolean vencida = c.getFechaLimite() != null
                        && c.getFechaLimite().isBefore(LocalDateTime.now())
                        && !c.estaCancelada();
                Color colorFila = vencida
                        ? new Color(254, 226, 226) : Color.WHITE;

                agregarCeldaColorPDF(tabla,
                        String.valueOf(c.getIdCuenta()),
                        Element.ALIGN_CENTER, colorFila);
                agregarCeldaColorPDF(tabla,
                        c.getProveedor().getNombre(),
                        Element.ALIGN_LEFT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + c.getMontoTotal().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + c.getTotalPagado().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        "RD$" + c.getSaldoPendiente().toPlainString(),
                        Element.ALIGN_RIGHT, colorFila);
                agregarCeldaColorPDF(tabla,
                        c.getFechaLimite() != null
                                ? c.getFechaLimite().format(FMT_FECHA) : "—",
                        Element.ALIGN_CENTER, colorFila);

                totalSaldo = totalSaldo.add(c.getSaldoPendiente());
            }

            doc.add(tabla);
            Paragraph total = new Paragraph(
                    "\nTotal saldo por pagar: RD$" + totalSaldo.toPlainString(),
                    new Font(Font.HELVETICA, 12, Font.BOLD,
                            new Color(220, 38, 38)));
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

            agregarPiePDF(doc, cuentas.size() + " cuentas");
            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ================================================================
    // Helpers PDF
    // ================================================================

    private void agregarTituloPDF(Document doc,
                                  String titulo,
                                  String subtitulo)
            throws DocumentException {
        Font fTitulo = new Font(Font.HELVETICA, 16, Font.BOLD,
                new Color(37, 99, 235));
        Font fSub    = new Font(Font.HELVETICA, 10, Font.NORMAL,
                new Color(100, 116, 139));

        Paragraph p = new Paragraph(titulo, fTitulo);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);

        Paragraph s = new Paragraph(subtitulo, fSub);
        s.setAlignment(Element.ALIGN_CENTER);
        doc.add(s);

        doc.add(new Chunk(new LineSeparator(1f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));
    }

    private void agregarPiePDF(Document doc, String nota)
            throws DocumentException {
        doc.add(new Paragraph(" "));
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                new Color(219, 234, 254), Element.ALIGN_CENTER, -2)));
        Font fPie = new Font(Font.HELVETICA, 8, Font.ITALIC,
                new Color(148, 163, 184));
        Paragraph pie = new Paragraph(
                nota + " — Generado el " + LocalDateTime.now().format(FMT)
                        + " — Jordis Technology", fPie);
        pie.setAlignment(Element.ALIGN_CENTER);
        doc.add(pie);
    }

    private void agregarEncabezadoPDF(PdfPTable tabla, String texto) {
        Font f = new Font(Font.HELVETICA, 9, Font.BOLD,
                new Color(30, 64, 175));
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setBackgroundColor(new Color(239, 246, 255));
        cell.setPadding(5);
        cell.setBorderColor(new Color(191, 219, 254));
        tabla.addCell(cell);
    }

    private void agregarCeldaPDF(PdfPTable tabla,
                                 String texto, int align) {
        agregarCeldaColorPDF(tabla, texto, align, Color.WHITE);
    }

    private void agregarCeldaColorPDF(PdfPTable tabla, String texto,
                                      int align, Color fondo) {
        Font f = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(fondo);
        cell.setBorderColor(new Color(219, 234, 254));
        tabla.addCell(cell);
    }

    private String tmpPath(String nombre) {
        return System.getProperty("java.io.tmpdir")
                + java.io.File.separator + nombre;
    }

    private String nvl(String s) {
        return s != null ? s : "—";
    }
}