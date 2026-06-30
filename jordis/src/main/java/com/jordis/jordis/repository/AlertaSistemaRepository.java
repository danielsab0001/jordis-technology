package com.jordis.jordis.repository;

import com.jordis.jordis.model.AlertaSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlertaSistemaRepository extends JpaRepository<AlertaSistema, Integer> {

    @Query("SELECT a FROM AlertaSistema a WHERE a.leida = false ORDER BY a.fechaHora DESC")
    List<AlertaSistema> findNoLeidas();

    @Query("SELECT a FROM AlertaSistema a ORDER BY a.fechaHora DESC")
    List<AlertaSistema> findTodas();

    @Query("SELECT COUNT(a) FROM AlertaSistema a WHERE a.leida = false")
    long contarNoLeidas();
}