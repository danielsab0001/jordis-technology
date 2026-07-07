package com.jordis.jordis.service;

import com.jordis.jordis.model.Configuracion;
import com.jordis.jordis.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository repo;

    public String obtener(String clave, String valorDefault) {
        return repo.findById(clave)
                .map(Configuracion::getValor)
                .orElse(valorDefault);
    }

    @Transactional
    public void guardar(String clave, String valor) {
        Configuracion c = repo.findById(clave)
                .orElse(new Configuracion(clave, valor, null));
        c.setValor(valor);
        repo.save(c);
    }
}