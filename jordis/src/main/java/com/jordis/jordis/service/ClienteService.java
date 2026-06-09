package com.jordis.jordis.service;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public List<Cliente> obtenerTodos() {
        return clienteRepository.findActivos();
    }

    public List<Cliente> buscar(String texto) {
        if (texto == null || texto.isBlank()) return obtenerTodos();
        return clienteRepository
                .buscarPorNombreOApellido(texto);
    }

    public Cliente buscarPorCedula(String cedula) {
        return clienteRepository.findByCedula(cedula)
                .orElseThrow(() -> new ClienteNoEncontradoException(
                        "No se encontró cliente con cédula: " + cedula));
    }

    @Transactional
    public Cliente crear(String nombre, String apellido, String cedula,
                         String telefono, String direccion) {

        if (clienteRepository.existeCedula(cedula)) {
            throw new CedulaDuplicadaException(
                    "Ya existe un cliente con la cédula " + cedula + ".");
        }

        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setApellido(apellido);
        cliente.setCedulaIdentificacion(cedula);
        cliente.setTelefono(telefono);
        cliente.setDireccion(direccion);
        cliente.setActivo(true);

        log.info("Cliente creado: {}", cliente.getNombreCompleto());
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Integer id, String nombre, String apellido,
                              String cedula, String telefono, String direccion) {

        Cliente cliente = obtenerPorId(id);

        if (clienteRepository.existeCedulaEnOtro(cedula, id)) {
            throw new CedulaDuplicadaException(
                    "Ya existe otro cliente con la cédula " + cedula + ".");
        }

        cliente.setNombre(nombre);
        cliente.setApellido(apellido);
        cliente.setCedulaIdentificacion(cedula);
        cliente.setTelefono(telefono);
        cliente.setDireccion(direccion);

        log.info("Cliente actualizado: {}", cliente.getNombreCompleto());
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void eliminar(Integer id) {
        Cliente cliente = obtenerPorId(id);
        cliente.setActivo(false);
        clienteRepository.save(cliente);
        log.info("Cliente eliminado (lógico): {}", cliente.getNombreCompleto());
    }

    public Cliente obtenerPorId(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException(
                        "Cliente con ID " + id + " no encontrado."));
    }

    // ---- Excepciones ----
    public static class CedulaDuplicadaException extends RuntimeException {
        public CedulaDuplicadaException(String msg) { super(msg); }
    }

    public static class ClienteNoEncontradoException extends RuntimeException {
        public ClienteNoEncontradoException(String msg) { super(msg); }
    }
}