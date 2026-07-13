package com.jordis.jordis.service;

import com.jordis.jordis.model.*;
import com.jordis.jordis.repository.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// OpenPDF
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteService {

    private final VentaRepository        ventaRepository;
    private final ProductoRepository     productoRepository;
    private final CompraRepository       compraRepository;
    private final ClienteRepository      clienteRepository;
    private final UsuarioRepository      usuarioRepository;
    private final ConfiguracionService   configuracionService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    // ── Colores PDF consistentes con el sistema ────────────────────────
    private static final Color COLOR_PRIMARIO  = new Color(37, 99, 235);
    private static final Color COLOR_ENCABEZADO= new Color(239, 246, 255);
    private static final Color COLOR_BORDE     = new Color(191, 219, 254);
    private static final Color COLOR_TEXTO_SEC = new Color(100, 116, 139);
    private static final Color COLOR_EXITO     = new Color(21, 128, 61);
    private static final Color COLOR_ALERTA    = new Color(220, 38, 38);
    private static final Color COLOR_ADVERTENCIA=new Color(180, 83, 9);

    private static final Font F_TITULO =
            new Font(Font.HELVETICA, 18, Font.BOLD, COLOR_PRIMARIO);
    private static final Font F_SUBTITULO =
            new Font(Font.HELVETICA, 11, Font.NORMAL, COLOR_TEXTO_SEC);
    private static final Font F_SECCION =
            new Font(Font.HELVETICA, 12, Font.BOLD, new Color(15, 23, 42));
    private static final Font F_HEADER =
            new Font(Font.HELVETICA, 9,  Font.BOLD,  new Color(30, 64, 175));
    private static final Font F_NORMAL =
            new Font(Font.HELVETICA, 9,  Font.NORMAL, Color.BLACK);
    private static final Font F_BOLD =
            new Font(Font.HELVETICA, 9,  Font.BOLD,   Color.BLACK);
    private static final Font F_TOTAL =
            new Font(Font.HELVETICA, 11, Font.BOLD,   COLOR_PRIMARIO);
    private static final Font F_SMALL =
            new Font(Font.HELVETICA, 8,  Font.NORMAL, COLOR_TEXTO_SEC);

    // ================================================================
    // DATOS — métodos que devuelven los datos para vista previa
    // ================================================================

    public List<Venta> obtenerVentas(LocalDateTime desde, LocalDateTime hasta,
                                     Integer idCliente, Integer idCajero,
                                     String metodoPago) {
        return ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .filter(v -> idCliente == null
                        || (v.getCliente() != null
                        && v.getCliente().getIdCliente().equals(idCliente)))
                .filter(v -> idCajero == null
                        || v.getCajero().getIdUsuario().equals(idCajero))
                .filter(v -> metodoPago == null || metodoPago.isBlank()
                        || v.getMetodoPago().equals(metodoPago))
                .sorted(Comparator.comparing(Venta::getFechaHora).reversed())
                .toList();
    }

    public List<Map<String, Object>> obtenerProductosVendidos(
            LocalDateTime desde, LocalDateTime hasta,
            Integer idCategoria, String marca) {

        Map<Producto, long[]> agrupado = new LinkedHashMap<>();
        ventaRepository.findEntreFechas(desde, hasta).stream()
                .filter(v -> !v.getAnulada())
                .flatMap(v -> v.getDetalles().stream())
                .filter(vp -> idCategoria == null
                        || (vp.getProducto().getCategoria() != null
                        && vp.getProducto().getCategoria()
                        .getIdCategoria().equals(idCategoria)))
                .filter(vp -> marca == null || marca.isBlank()
                        || (vp.getProducto().getMarca() != null
                        && vp.getProducto().getMarca()
                        .equalsIgnoreCase(marca)))
                .forEach(vp -> {
                    agrupado.computeIfAbsent(
                            vp.getProducto(), k -> new long[2]);
                    agrupado.get(vp.getProducto())[0] += vp.getCantidad();
                    agrupado.get(vp.getProducto())[1] +=
                            vp.getSubtotal().longValue();
                });

        return agrupado.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue()[0], a.getValue()[0]))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("producto",  e.getKey());
                    m.put("nombre",    e.getKey().getNombre());
                    m.put("marca",     e.getKey().getMarca() != null
                            ? e.getKey().getMarca() : "—");
                    m.put("categoria", e.getKey().getCategoria() != null
                            ? e.getKey().getCategoria().getNombre() : "—");
                    m.put("unidades",  e.getValue()[0]);
                    m.put("ingresos",  BigDecimal.valueOf(e.getValue()[1]));
                    return m;
                })
                .toList();
    }

    public List<Compra> obtenerCompras(LocalDateTime desde,
                                       LocalDateTime hasta,
                                       Integer idProveedor) {
        return compraRepository.findTodas().stream()
                .filter(c -> !c.getFechaPedido().isBefore(desde)
                        && !c.getFechaPedido().isAfter(hasta))
                .filter(c -> idProveedor == null
                        || c.getProveedor().getIdProveedor().equals(idProveedor))
                .sorted(Comparator.comparing(Compra::getFechaPedido).reversed())
                .toList();
    }

    public Map<String, Object> obtenerDatosCreditos(
            LocalDateTime desde, LocalDateTime hasta) {

        List<Venta> todos = ventaRepository.findCreditos();
        List<Venta> enPeriodo = todos.stream()
                .filter(v -> !v.getFechaHora().isBefore(desde)
                        && !v.getFechaHora().isAfter(hasta))
                .toList();

        List<Venta> pendientes = todos.stream()
                .filter(v -> !v.estaCancelado()).toList();
        List<Venta> pagados    = todos.stream()
                .filter(Venta::estaCancelado).toList();
        List<Venta> vencidos   = todos.stream()
                .filter(v -> !v.estaCancelado()
                        && v.getFechaLimiteCredito() != null
                        && v.getFechaLimiteCredito()
                        .isBefore(LocalDateTime.now())).toList();

        BigDecimal totalOtorgado = enPeriodo.stream()
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPendiente = pendientes.stream()
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVencido = vencidos.stream()
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enPeriodo",      enPeriodo);
        r.put("pendientes",     pendientes);
        r.put("pagados",        pagados);
        r.put("vencidos",       vencidos);
        r.put("totalOtorgado",  totalOtorgado);
        r.put("totalPendiente", totalPendiente);
        r.put("totalVencido",   totalVencido);
        return r;
    }

    public Map<String, Object> obtenerUtilidades(
            LocalDateTime desde, LocalDateTime hasta) {

        List<Venta> ventas = ventaRepository.findEntreFechas(desde, hasta)
                .stream().filter(v -> !v.getAnulada()).toList();

        BigDecimal totalVendido = ventas.stream()
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Costo basado en último precio de compra de cada producto
        BigDecimal costoVentas = ventas.stream()
                .flatMap(v -> v.getDetalles().stream())
                .map(vp -> {
                    BigDecimal costo = vp.getProducto().getUltimoPrecioCompra();
                    if (costo == null) costo = vp.getPrecioUnitario()
                            .multiply(new BigDecimal("0.70"));
                    return costo.multiply(BigDecimal.valueOf(vp.getCantidad()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ganancia = totalVendido.subtract(costoVentas);
        BigDecimal margen = totalVendido.compareTo(BigDecimal.ZERO) > 0
                ? ganancia.divide(totalVendido, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Desglose por día
        Map<String, BigDecimal[]> porDia = new LinkedHashMap<>();
        ventas.forEach(v -> {
            String dia = v.getFechaHora().format(FMT_FECHA);
            porDia.computeIfAbsent(dia, k -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO});
            porDia.get(dia)[0] = porDia.get(dia)[0].add(v.getTotal());
        });

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ventas",       ventas);
        r.put("totalVendido", totalVendido);
        r.put("costoVentas",  costoVentas);
        r.put("ganancia",     ganancia);
        r.put("margen",       margen);
        r.put("porDia",       porDia);
        return r;
    }

    // Helpers de acceso a catálogos
    public List<Cliente>  obtenerClientes()    {
        return clienteRepository.findActivos();
    }
    public List<Usuario>  obtenerCajeros()     {
        return usuarioRepository.findAll();
    }
    public List<Categoria> obtenerCategorias() {
        return productoRepository.findByActivoTrue().stream()
                .map(Producto::getCategoria)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Categoria::getNombre))
                .toList();
    }
    public List<String> obtenerMarcas() {
        return productoRepository.findByActivoTrue().stream()
                .map(Producto::getMarca)
                .filter(Objects::nonNull)
                .distinct().sorted().toList();
    }
    public List<Proveedor> obtenerProveedores() {
        // Necesita ProveedorRepository — inyecta si no está
        return compraRepository.findTodas().stream()
                .map(Compra::getProveedor)
                .distinct()
                .sorted(Comparator.comparing(Proveedor::getNombre))
                .toList();
    }

    // ================================================================
    // PDF — generación
    // ================================================================

    private Document abrirDoc(String ruta, boolean horizontal)
            throws Exception {
        Document doc = horizontal
                ? new Document(PageSize.A4.rotate(), 30, 30, 40, 40)
                : new Document(PageSize.A4, 36, 36, 48, 48);
        PdfWriter writer =
                PdfWriter.getInstance(doc, new FileOutputStream(ruta));
        // Pie de página con número
        writer.setPageEvent(new PiePageEvent());
        doc.open();
        return doc;
    }

    private void encabezadoPDF(Document doc, String titulo,
                               String subtitulo, String filtros)
            throws DocumentException {
        String negocio = configuracionService.obtener(
                "negocio.nombre", "Jordis Technology");

        Paragraph emp = new Paragraph(negocio, F_TITULO);
        emp.setAlignment(Element.ALIGN_LEFT);
        doc.add(emp);

        Paragraph rep = new Paragraph(titulo,
                new Font(Font.HELVETICA, 14, Font.BOLD,
                        new Color(15, 23, 42)));
        rep.setSpacingBefore(2);
        doc.add(rep);

        if (subtitulo != null) {
            doc.add(new Paragraph(subtitulo, F_SUBTITULO));
        }
        if (filtros != null) {
            doc.add(new Paragraph("Filtros: " + filtros, F_SMALL));
        }

        doc.add(new Paragraph(" "));
        LineSeparator sep = new LineSeparator(1f, 100f,
                COLOR_BORDE, Element.ALIGN_CENTER, -2);
        doc.add(new Chunk(sep));
        doc.add(new Paragraph(" "));
    }

    private void kpiPDF(Document doc, String[][] kpis)
            throws DocumentException {
        PdfPTable tabla = new PdfPTable(kpis.length);
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(12);

        for (String[] kpi : kpis) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(COLOR_BORDE);
            cell.setBackgroundColor(COLOR_ENCABEZADO);
            cell.setPadding(8);

            Paragraph p = new Paragraph();
            p.add(new Chunk(kpi[0] + "\n",
                    new Font(Font.HELVETICA, 8, Font.BOLD, COLOR_TEXTO_SEC)));
            p.add(new Chunk(kpi[1],
                    new Font(Font.HELVETICA, 14, Font.BOLD, COLOR_PRIMARIO)));
            cell.addElement(p);
            tabla.addCell(cell);
        }
        doc.add(tabla);
    }

    private PdfPTable crearTabla(float[] anchos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(anchos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(anchos);
        tabla.setSpacingAfter(10);
        return tabla;
    }

    private void encabezadoTabla(PdfPTable tabla, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, F_HEADER));
            c.setBackgroundColor(COLOR_ENCABEZADO);
            c.setPadding(5);
            c.setBorderColor(COLOR_BORDE);
            tabla.addCell(c);
        }
    }

    private void celda(PdfPTable t, String texto, int align) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_NORMAL));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(COLOR_BORDE);
        t.addCell(c);
    }

    private void celdaBold(PdfPTable t, String texto, int align) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_BOLD));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(COLOR_BORDE);
        c.setBackgroundColor(new Color(248, 250, 252));
        t.addCell(c);
    }

    private void filaTotal(Document doc, String etiqueta, String valor)
            throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(40);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setWidths(new float[]{2f, 1.5f});

        PdfPCell cE = new PdfPCell(new Phrase(etiqueta, F_TOTAL));
        cE.setBorder(Rectangle.TOP);
        cE.setBorderColor(COLOR_BORDE);
        cE.setPadding(4);
        cE.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(cE);

        PdfPCell cV = new PdfPCell(new Phrase(valor, F_TOTAL));
        cV.setBorder(Rectangle.TOP);
        cV.setBorderColor(COLOR_BORDE);
        cV.setPadding(4);
        cV.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(cV);
        doc.add(t);
    }

    private void piePDF(Document doc, String nota)
            throws DocumentException {
        doc.add(new Paragraph(" "));
        doc.add(new Chunk(new LineSeparator(0.5f, 100f,
                COLOR_BORDE, Element.ALIGN_CENTER, -2)));
        Paragraph pie = new Paragraph(
                nota + "  ·  Generado el "
                        + LocalDateTime.now().format(FMT)
                        + "  ·  " + configuracionService.obtener(
                        "negocio.nombre", "Jordis Technology"), F_SMALL);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setSpacingBefore(4);
        doc.add(pie);
    }

    // ── 1. Reporte de Ventas ─────────────────────────────────────────

    public String generarPdfVentas(List<Venta> ventas,
                                   LocalDateTime desde, LocalDateTime hasta,
                                   String filtrosTexto) {
        String ruta = tmpPath("reporte_ventas_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = abrirDoc(ruta, true);

            encabezadoPDF(doc, "Reporte de Ventas",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA), filtrosTexto);

            BigDecimal total = ventas.stream()
                    .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ticket = ventas.isEmpty() ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(ventas.size()),
                    2, RoundingMode.HALF_UP);

            kpiPDF(doc, new String[][]{
                    {"TOTAL VENDIDO",      "RD$" + fmt(total)},
                    {"CANTIDAD DE VENTAS", String.valueOf(ventas.size())},
                    {"TICKET PROMEDIO",    "RD$" + fmt(ticket)},
                    {"VENTAS A CRÉDITO",   String.valueOf(
                            ventas.stream().filter(Venta::getEsCredito).count())}
            });

            PdfPTable tabla = crearTabla(
                    new float[]{2f, 2.5f, 3f, 4f, 2f, 1.5f, 2f, 2f});
            encabezadoTabla(tabla,
                    "Factura","Fecha","Cliente","Productos",
                    "Subtotal","Desc.","Total","Pago");

            for (Venta v : ventas) {
                String prods = v.getDetalles().stream()
                        .map(vp -> vp.getProducto().getNombre()
                                + " x" + vp.getCantidad())
                        .collect(Collectors.joining(", "));
                celda(tabla, nvl(v.getNumeroFactura()),
                        Element.ALIGN_CENTER);
                celda(tabla, v.getFechaHora().format(FMT),
                        Element.ALIGN_CENTER);
                celda(tabla, v.getCliente() != null
                                ? v.getCliente().getNombreCompleto() : "Ocasional",
                        Element.ALIGN_LEFT);
                celda(tabla, truncar(prods, 50),
                        Element.ALIGN_LEFT);
                celda(tabla, "RD$" + fmt(v.getSubtotal()),
                        Element.ALIGN_RIGHT);
                celda(tabla, v.getDescuentoPorcentual()
                                .compareTo(BigDecimal.ZERO) > 0
                                ? v.getDescuentoPorcentual().toPlainString() + "%" : "—",
                        Element.ALIGN_CENTER);
                celda(tabla, "RD$" + fmt(v.getTotal()),
                        Element.ALIGN_RIGHT);
                celda(tabla, v.getMetodoPago(),
                        Element.ALIGN_CENTER);
            }
            doc.add(tabla);
            filaTotal(doc, "TOTAL VENDIDO:", "RD$" + fmt(total));
            piePDF(doc, ventas.size() + " ventas");
            doc.close();
            log.info("PDF ventas generado: {}", ruta);
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ── 2. Reporte de Productos Vendidos ─────────────────────────────

    public String generarPdfProductos(List<Map<String, Object>> datos,
                                      LocalDateTime desde,
                                      LocalDateTime hasta,
                                      String filtrosTexto) {
        String ruta = tmpPath("reporte_productos_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = abrirDoc(ruta, false);
            encabezadoPDF(doc, "Reporte de Productos Vendidos",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA), filtrosTexto);

            long totalUnidades = datos.stream()
                    .mapToLong(m -> (long) m.get("unidades")).sum();
            BigDecimal totalIngresos = datos.stream()
                    .map(m -> (BigDecimal) m.get("ingresos"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            kpiPDF(doc, new String[][]{
                    {"PRODUCTOS DIFERENTES", String.valueOf(datos.size())},
                    {"UNIDADES VENDIDAS",    String.valueOf(totalUnidades)},
                    {"INGRESOS GENERADOS",   "RD$" + fmt(totalIngresos)}
            });

            PdfPTable tabla = crearTabla(
                    new float[]{0.5f, 3f, 2f, 2f, 1.5f, 2f});
            encabezadoTabla(tabla,
                    "#","Producto","Marca","Categoría","Unidades","Ingresos");

            for (int i = 0; i < datos.size(); i++) {
                var m = datos.get(i);
                // Medalla para top 3
                String pos = i == 0 ? "🥇" : i == 1 ? "🥈"
                        : i == 2 ? "🥉" : String.valueOf(i + 1);
                celda(tabla, pos,          Element.ALIGN_CENTER);
                celda(tabla, (String) m.get("nombre"),
                        Element.ALIGN_LEFT);
                celda(tabla, (String) m.get("marca"),
                        Element.ALIGN_CENTER);
                celda(tabla, (String) m.get("categoria"),
                        Element.ALIGN_CENTER);
                celda(tabla, String.valueOf(m.get("unidades")),
                        Element.ALIGN_CENTER);
                celda(tabla, "RD$" + fmt((BigDecimal) m.get("ingresos")),
                        Element.ALIGN_RIGHT);
            }
            doc.add(tabla);
            filaTotal(doc, "TOTAL INGRESOS:", "RD$" + fmt(totalIngresos));
            piePDF(doc, datos.size() + " productos");
            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ── 3. Reporte de Compras ────────────────────────────────────────

    public String generarPdfCompras(List<Compra> compras,
                                    LocalDateTime desde,
                                    LocalDateTime hasta,
                                    String filtrosTexto) {
        String ruta = tmpPath("reporte_compras_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = abrirDoc(ruta, true);
            encabezadoPDF(doc, "Reporte de Compras",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA), filtrosTexto);

            BigDecimal total = compras.stream()
                    .map(Compra::getTotalCompra)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long recibidas = compras.stream()
                    .filter(c -> "RECIBIDA".equals(c.getEstado())).count();
            long pendientes = compras.stream()
                    .filter(c -> "PENDIENTE".equals(c.getEstado())).count();

            kpiPDF(doc, new String[][]{
                    {"TOTAL COMPRAS",    String.valueOf(compras.size())},
                    {"MONTO TOTAL",      "RD$" + fmt(total)},
                    {"RECIBIDAS",        String.valueOf(recibidas)},
                    {"PENDIENTES",       String.valueOf(pendientes)}
            });

            PdfPTable tabla = crearTabla(
                    new float[]{0.8f, 2.5f, 2.5f, 4f, 2f, 1.8f});
            encabezadoTabla(tabla,
                    "#","Fecha","Proveedor","Productos","Total","Estado");

            for (Compra c : compras) {
                String prods = c.getDetalles().stream()
                        .map(d -> d.getProducto().getNombre()
                                + " x" + d.getCantidad())
                        .collect(Collectors.joining(", "));
                celda(tabla, String.valueOf(c.getIdCompra()),
                        Element.ALIGN_CENTER);
                celda(tabla, c.getFechaPedido().format(FMT),
                        Element.ALIGN_CENTER);
                celda(tabla, c.getProveedor().getNombre(),
                        Element.ALIGN_LEFT);
                celda(tabla, truncar(prods, 50),
                        Element.ALIGN_LEFT);
                celda(tabla, "RD$" + fmt(c.getTotalCompra()),
                        Element.ALIGN_RIGHT);

                // Celda de estado con color
                PdfPCell cEst = new PdfPCell();
                cEst.setBorderColor(COLOR_BORDE);
                cEst.setPadding(4);
                Color colorEst = switch (c.getEstado()) {
                    case "RECIBIDA"  -> COLOR_EXITO;
                    case "CANCELADA" -> COLOR_ALERTA;
                    default          -> COLOR_ADVERTENCIA;
                };
                Phrase phEst = new Phrase(c.getEstado(),
                        new Font(Font.HELVETICA, 9, Font.BOLD, colorEst));
                cEst.setPhrase(phEst);
                cEst.setHorizontalAlignment(Element.ALIGN_CENTER);
                tabla.addCell(cEst);
            }
            doc.add(tabla);
            filaTotal(doc, "TOTAL COMPRADO:", "RD$" + fmt(total));
            piePDF(doc, compras.size() + " compras");
            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ── 4. Reporte de Créditos ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    public String generarPdfCreditos(Map<String, Object> datos,
                                     LocalDateTime desde,
                                     LocalDateTime hasta) {
        String ruta = tmpPath("reporte_creditos_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = abrirDoc(ruta, true);
            encabezadoPDF(doc, "Reporte de Créditos",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA), null);

            kpiPDF(doc, new String[][]{
                    {"OTORGADO EN PERÍODO",
                            "RD$" + fmt((BigDecimal) datos.get("totalOtorgado"))},
                    {"PENDIENTE TOTAL",
                            "RD$" + fmt((BigDecimal) datos.get("totalPendiente"))},
                    {"VENCIDO",
                            "RD$" + fmt((BigDecimal) datos.get("totalVencido"))},
                    {"CRÉDITOS PAGADOS",
                            String.valueOf(
                                    ((List<?>) datos.get("pagados")).size())}
            });

            // Sección 1: Créditos del período
            List<Venta> enPeriodo = (List<Venta>) datos.get("enPeriodo");
            doc.add(new Paragraph("Créditos otorgados en el período",
                    F_SECCION));
            doc.add(new Paragraph(" "));
            tablaCreditos(doc, enPeriodo);

            // Sección 2: Créditos vencidos
            List<Venta> vencidos = (List<Venta>) datos.get("vencidos");
            if (!vencidos.isEmpty()) {
                doc.add(new Paragraph(" "));
                Paragraph pVenc = new Paragraph(
                        "Créditos vencidos (" + vencidos.size() + ")",
                        new Font(Font.HELVETICA, 12, Font.BOLD, COLOR_ALERTA));
                doc.add(pVenc);
                doc.add(new Paragraph(" "));
                tablaCreditos(doc, vencidos);
            }

            piePDF(doc,
                    enPeriodo.size() + " créditos en el período");
            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    private void tablaCreditos(Document doc, List<Venta> lista)
            throws DocumentException {
        if (lista.isEmpty()) {
            doc.add(new Paragraph("  Sin registros.", F_SMALL));
            return;
        }
        PdfPTable tabla = crearTabla(
                new float[]{2f, 2.5f, 3f, 2f, 2f, 2f, 2f});
        encabezadoTabla(tabla,
                "Factura","Fecha","Cliente","Total",
                "Pagado","Saldo","Vence");
        for (Venta v : lista) {
            celda(tabla, nvl(v.getNumeroFactura()),
                    Element.ALIGN_CENTER);
            celda(tabla, v.getFechaHora().format(FMT_FECHA),
                    Element.ALIGN_CENTER);
            celda(tabla, v.getCliente() != null
                            ? v.getCliente().getNombreCompleto() : "—",
                    Element.ALIGN_LEFT);
            celda(tabla, "RD$" + fmt(v.getTotal()),
                    Element.ALIGN_RIGHT);
            celda(tabla, "RD$" + fmt(v.getTotalPagado()),
                    Element.ALIGN_RIGHT);
            celda(tabla, "RD$" + fmt(v.getSaldoPendiente()),
                    Element.ALIGN_RIGHT);
            celda(tabla, v.getFechaLimiteCredito() != null
                            ? v.getFechaLimiteCredito().format(FMT_FECHA) : "—",
                    Element.ALIGN_CENTER);
        }
        doc.add(tabla);
    }

    // ── 5. Reporte de Utilidades ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    public String generarPdfUtilidades(Map<String, Object> datos,
                                       LocalDateTime desde,
                                       LocalDateTime hasta) {
        String ruta = tmpPath("reporte_utilidades_"
                + LocalDateTime.now().format(FMT_ARCHIVO) + ".pdf");
        try {
            Document doc = abrirDoc(ruta, false);
            encabezadoPDF(doc, "Reporte de Utilidades",
                    "Período: " + desde.format(FMT_FECHA)
                            + " — " + hasta.format(FMT_FECHA), null);

            BigDecimal tv  = (BigDecimal) datos.get("totalVendido");
            BigDecimal cv  = (BigDecimal) datos.get("costoVentas");
            BigDecimal gan = (BigDecimal) datos.get("ganancia");
            BigDecimal mar = (BigDecimal) datos.get("margen");
            List<Venta> ventas = (List<Venta>) datos.get("ventas");

            kpiPDF(doc, new String[][]{
                    {"VENTAS TOTALES",   "RD$" + fmt(tv)},
                    {"COSTO DE VENTAS",  "RD$" + fmt(cv)},
                    {"GANANCIA BRUTA",   "RD$" + fmt(gan)},
                    {"MARGEN",           mar.setScale(1,
                            RoundingMode.HALF_UP).toPlainString() + "%"}
            });

            // Tabla de resumen financiero
            PdfPTable tRes = crearTabla(new float[]{3f, 2f, 2f});
            encabezadoTabla(tRes,
                    "Concepto", "Monto (RD$)", "% del Total");
            celdaBold(tRes, "Ingresos por ventas", Element.ALIGN_LEFT);
            celdaBold(tRes, fmt(tv), Element.ALIGN_RIGHT);
            celdaBold(tRes, "100%", Element.ALIGN_CENTER);

            celda(tRes, "(-) Costo de ventas", Element.ALIGN_LEFT);
            PdfPCell cCosto = new PdfPCell(
                    new Phrase(fmt(cv),
                            new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_ALERTA)));
            cCosto.setPadding(4);
            cCosto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cCosto.setBorderColor(COLOR_BORDE);
            tRes.addCell(cCosto);
            BigDecimal pctCosto = tv.compareTo(BigDecimal.ZERO) > 0
                    ? cv.divide(tv, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            celda(tRes,
                    pctCosto.setScale(1, RoundingMode.HALF_UP) + "%",
                    Element.ALIGN_CENTER);

            // Fila ganancia
            PdfPCell cGanLbl = new PdfPCell(
                    new Phrase("(=) Ganancia bruta",
                            new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_EXITO)));
            cGanLbl.setPadding(6);
            cGanLbl.setBackgroundColor(new Color(240, 253, 244));
            cGanLbl.setBorderColor(COLOR_BORDE);
            tRes.addCell(cGanLbl);

            PdfPCell cGanVal = new PdfPCell(
                    new Phrase(fmt(gan),
                            new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_EXITO)));
            cGanVal.setPadding(6);
            cGanVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cGanVal.setBackgroundColor(new Color(240, 253, 244));
            cGanVal.setBorderColor(COLOR_BORDE);
            tRes.addCell(cGanVal);

            PdfPCell cGanPct = new PdfPCell(
                    new Phrase(mar.setScale(1,
                            RoundingMode.HALF_UP).toPlainString() + "%",
                            new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_EXITO)));
            cGanPct.setPadding(6);
            cGanPct.setHorizontalAlignment(Element.ALIGN_CENTER);
            cGanPct.setBackgroundColor(new Color(240, 253, 244));
            cGanPct.setBorderColor(COLOR_BORDE);
            tRes.addCell(cGanPct);

            doc.add(tRes);
            doc.add(new Paragraph(" "));

            // Detalle de ventas
            doc.add(new Paragraph(
                    "Detalle de ventas del período", F_SECCION));
            doc.add(new Paragraph(" "));

            PdfPTable tDet = crearTabla(
                    new float[]{2f, 2f, 3f, 2f, 2f, 2f});
            encabezadoTabla(tDet,
                    "Factura","Fecha","Cliente","Venta","Costo est.","Ganancia");

            for (Venta v : ventas) {
                BigDecimal costoV = v.getDetalles().stream()
                        .map(vp -> {
                            BigDecimal c = vp.getProducto()
                                    .getUltimoPrecioCompra();
                            if (c == null) c = vp.getPrecioUnitario()
                                    .multiply(new BigDecimal("0.70"));
                            return c.multiply(
                                    BigDecimal.valueOf(vp.getCantidad()));
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal gananciaV = v.getTotal().subtract(costoV);

                celda(tDet, nvl(v.getNumeroFactura()),
                        Element.ALIGN_CENTER);
                celda(tDet, v.getFechaHora().format(FMT_FECHA),
                        Element.ALIGN_CENTER);
                celda(tDet, v.getCliente() != null
                        ? truncar(v.getCliente().getNombreCompleto(), 30)
                        : "Ocasional", Element.ALIGN_LEFT);
                celda(tDet, "RD$" + fmt(v.getTotal()),
                        Element.ALIGN_RIGHT);
                celda(tDet, "RD$" + fmt(costoV),
                        Element.ALIGN_RIGHT);

                // Ganancia por venta coloreada
                PdfPCell cG = new PdfPCell(new Phrase(
                        "RD$" + fmt(gananciaV),
                        new Font(Font.HELVETICA, 9, Font.BOLD,
                                gananciaV.compareTo(BigDecimal.ZERO) >= 0
                                        ? COLOR_EXITO : COLOR_ALERTA)));
                cG.setPadding(4);
                cG.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cG.setBorderColor(COLOR_BORDE);
                tDet.addCell(cG);
            }
            doc.add(tDet);

            piePDF(doc, ventas.size() + " ventas analizadas");
            doc.close();
            return ruta;
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v.doubleValue());
    }

    private String nvl(String s) { return s != null ? s : "—"; }

    private String truncar(String s, int max) {
        return s != null && s.length() > max
                ? s.substring(0, max) + "…" : nvl(s);
    }

    private String tmpPath(String nombre) {
        return System.getProperty("java.io.tmpdir")
                + java.io.File.separator + nombre;
    }

    // Evento de pie de página con número
    static class PiePageEvent extends PdfPageEventHelper {
        private static final Font F =
                new Font(Font.HELVETICA, 7, Font.NORMAL,
                        new Color(148, 163, 184));

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase pie = new Phrase(
                    "Página " + writer.getPageNumber(), F);
            ColumnText.showTextAligned(cb,
                    Element.ALIGN_RIGHT, pie,
                    doc.right(), doc.bottom() - 10, 0);
        }
    }
}