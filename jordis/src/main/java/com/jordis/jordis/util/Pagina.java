package com.jordis.jordis.util;

import java.util.List;

/**
 * Resultado de una consulta paginada a nivel de base de datos (LIMIT/OFFSET
 * reales, no una lista completa cortada en memoria). Lo usa
 * {@link PaginadorRemoto} para saber qué mostrar y cuántas páginas hay,
 * sin necesitar cargar más que la página actual.
 */
public record Pagina<T>(
        List<T> contenido,
        int numeroPagina,
        int totalPaginas,
        long totalRegistros
) {
    public static <T> Pagina<T> vacia() {
        return new Pagina<>(List.of(), 0, 0, 0);
    }
}