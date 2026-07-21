package com.jordis.jordis.service;

import com.jordis.jordis.model.Proveedor;
import com.jordis.jordis.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final AutenticacionService autenticacionService;
    private final AuditoriaService auditoriaService;

    public List<Proveedor> obtenerTodos() {
        return proveedorRepository.findActivos();
    }

    public List<Proveedor> obtenerTodosIncluyendoInactivos() {
        return proveedorRepository.findAll();
    }

    public List<Proveedor> buscar(String texto) {
        if (texto == null || texto.isBlank()) return obtenerTodos();
        return proveedorRepository.buscarPorNombre(texto);
    }

    @Transactional
    public Proveedor crear(String nombre, String contacto, String telefono,
                           String correo, String direccion, String descripcion) {
        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setContacto(contacto);
        p.setTelefono(telefono);
        p.setCorreo(correo);
        p.setDireccion(direccion);
        p.setDescripcion(descripcion);
        p.setActivo(true);
        Proveedor guardado = proveedorRepository.save(p);
        log.info("Proveedor creado: {}", nombre);
        auditoriaService.registrar(autenticacionService.getUsuarioActivo(),
                "PROVEEDOR_CREADO", "Proveedor", guardado.getIdProveedor(), nombre);
        return guardado;
    }

    @Transactional
    public Proveedor actualizar(Integer id, String nombre, String contacto,
                                String telefono, String correo,
                                String direccion, String descripcion) {
        Proveedor p = obtenerPorId(id);
        p.setNombre(nombre);
        p.setContacto(contacto);
        p.setTelefono(telefono);
        p.setCorreo(correo);
        p.setDireccion(direccion);
        p.setDescripcion(descripcion);
        log.info("Proveedor actualizado: {}", nombre);
        return proveedorRepository.save(p);
    }

    @Transactional
    public void desactivar(Integer id) {
        Proveedor p = obtenerPorId(id);
        p.setActivo(false);
        proveedorRepository.save(p);
        log.info("Proveedor desactivado: {}", p.getNombre());
    }

    @Transactional
    public void activar(Integer id) {
        Proveedor p = obtenerPorId(id);
        p.setActivo(true);
        proveedorRepository.save(p);
        log.info("Proveedor activado: {}", p.getNombre());
    }

    @Transactional
    public void eliminar(Integer id) {
        Proveedor p = obtenerPorId(id);
        proveedorRepository.delete(p);
        log.info("Proveedor eliminado permanentemente: {}", p.getNombre());
    }

    public Proveedor obtenerPorId(Integer id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado: " + id));
    }
}