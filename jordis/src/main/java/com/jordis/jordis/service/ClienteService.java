package com.jordis.jordis.service;

import com.jordis.jordis.model.Cliente;
import com.jordis.jordis.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
        return clienteRepository.buscarPorNombreOApellido(texto);
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
        Cliente c = new Cliente();
        c.setTipoCliente("PERSONA");
        c.setNombre(nombre);
        c.setApellido(apellido);
        c.setCedulaIdentificacion(cedula);
        c.setTelefono(telefono);
        c.setDireccion(direccion);
        c.setActivo(true);
        log.info("Cliente persona creado: {} {}", nombre, apellido);
        return clienteRepository.save(c);
    }

    @Transactional
    public Cliente crearEmpresa(String razonSocial, String rnc,
                                String contactoPrincipal, String telefono,
                                String direccion) {
        if (rnc != null && !rnc.isBlank() && clienteRepository.existeRnc(rnc)) {
            throw new RncDuplicadoException(
                    "Ya existe una empresa con el RNC " + rnc + ".");
        }
        Cliente c = new Cliente();
        c.setTipoCliente("EMPRESA");
        c.setNombre(razonSocial); // nombre se usa como razón social en empresa
        c.setRazonSocial(razonSocial);
        c.setRnc(rnc);
        c.setContactoPrincipal(contactoPrincipal);
        c.setTelefono(telefono);
        c.setDireccion(direccion);
        c.setActivo(true);
        log.info("Cliente empresa creado: {}", razonSocial);
        return clienteRepository.save(c);
    }

    @Transactional
    public Cliente actualizar(Integer id, String nombre, String apellido,
                              String identificador, String telefono,
                              String direccion, String contactoPrincipal,
                              String tipoCliente) {
        Cliente c = obtenerPorId(id);

        if ("EMPRESA".equals(tipoCliente)) {
            if (clienteRepository.existeRncEnOtro(identificador, id)) {
                throw new RncDuplicadoException(
                        "Ya existe otra empresa con el RNC " + identificador + ".");
            }
            c.setRazonSocial(nombre);
            c.setNombre(nombre);
            c.setRnc(identificador);
            c.setContactoPrincipal(contactoPrincipal);
        } else {
            if (clienteRepository.existeCedulaEnOtro(identificador, id)) {
                throw new CedulaDuplicadaException(
                        "Ya existe otro cliente con la cédula " + identificador + ".");
            }
            c.setNombre(nombre);
            c.setApellido(apellido);
            c.setCedulaIdentificacion(identificador);
        }
        c.setTelefono(telefono);
        c.setDireccion(direccion);
        return clienteRepository.save(c);
    }

    @Transactional
    public void eliminar(Integer id) {
        Cliente c = obtenerPorId(id);
        c.setActivo(false);
        clienteRepository.save(c);
    }

    public Cliente obtenerPorId(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException(
                        "Cliente con ID " + id + " no encontrado."));
    }

    public static class CedulaDuplicadaException extends RuntimeException {
        public CedulaDuplicadaException(String msg) { super(msg); }
    }
    public static class RncDuplicadoException extends RuntimeException {
        public RncDuplicadoException(String msg) { super(msg); }
    }
    public static class ClienteNoEncontradoException extends RuntimeException {
        public ClienteNoEncontradoException(String msg) { super(msg); }
    }
}