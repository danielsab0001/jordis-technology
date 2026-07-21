package com.jordis.jordis.repository;

import com.jordis.jordis.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Integer> {

    @Query("SELECT a FROM AuditoriaLog a ORDER BY a.fechaHora DESC")
    List<AuditoriaLog> findTodas();

    @Query("SELECT a FROM AuditoriaLog a WHERE a.entidad = :entidad AND a.idEntidad = :idEntidad ORDER BY a.fechaHora DESC")
    List<AuditoriaLog> findPorEntidad(String entidad, Integer idEntidad);
}