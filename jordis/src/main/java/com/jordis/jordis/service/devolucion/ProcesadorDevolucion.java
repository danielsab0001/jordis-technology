package com.jordis.jordis.service.devolucion;

import com.jordis.jordis.model.Devolucion;
import com.jordis.jordis.model.TipoDevolucion;

public interface ProcesadorDevolucion {

    TipoDevolucion getTipo();

    void procesar(Devolucion devolucion);
}