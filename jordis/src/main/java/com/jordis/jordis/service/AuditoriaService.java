package com.jordis.jordis.service;

import com.jordis.jordis.model.AuditoriaLog;
import com.jordis.jordis.model.Usuario;
import com.jordis.jordis.repository.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    private final AuditoriaLogRepository auditoriaLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Usuario usuario, String accion, String entidad,
                          Integer idEntidad, String detalle) {
        try {
            AuditoriaLog registro = new AuditoriaLog();
            registro.setUsuario(usuario);
            registro.setAccion(accion);
            registro.setEntidad(entidad);
            registro.setIdEntidad(idEntidad);
            registro.setDetalle(detalle);
            auditoriaLogRepository.save(registro);
        } catch (Exception e) {
            log.error("No se pudo registrar auditoría: {} / {} #{}",
                    accion, entidad, idEntidad, e);
        }
    }

    public List<AuditoriaLog> obtenerTodas() {
        return auditoriaLogRepository.findTodas();
    }

    public List<AuditoriaLog> obtenerPorEntidad(String entidad, Integer idEntidad) {
        return auditoriaLogRepository.findPorEntidad(entidad, idEntidad);
    }
}