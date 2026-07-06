package com.jordis.jordis.repository;

import com.jordis.jordis.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionRepository
        extends JpaRepository<Configuracion, String> {}