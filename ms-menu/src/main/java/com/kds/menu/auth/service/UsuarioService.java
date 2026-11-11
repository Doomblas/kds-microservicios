package com.kds.menu.auth.service;

import com.kds.menu.auth.dto.EstadoRequest;
import com.kds.menu.auth.dto.RolRequest;
import com.kds.menu.auth.dto.UsuarioResponse;
import com.kds.menu.auth.exception.OperacionUsuarioNoPermitidaException;
import com.kds.menu.auth.exception.UsuarioNoEncontradoException;
import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;
import com.kds.menu.auth.repository.UsuarioRepository;
import com.kds.menu.exception.SolicitudInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String busqueda) {
        String termino = busqueda == null ? "" : busqueda.trim();

        List<Usuario> usuarios = termino.isEmpty()
                ? usuarioRepository.findAllByOrderByFechaCreacionDesc()
                : usuarioRepository
                        .findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByFechaCreacionDesc(
                                termino,
                                termino);

        return usuarios.stream().map(UsuarioResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.desde(buscar(id));
    }

    /**
     * Asignar un rol a una cuenta PENDIENTE la activa. El estado y el rol se
     * guardan en columnas distintas; esta regla evita obligar al ADMIN a hacer
     * dos operaciones para habilitar una cuenta recién registrada.
     */
    @Transactional
    public UsuarioResponse asignarRol(Long id, RolRequest request) {
        if (request.rol() == Rol.ADMIN) {
            throw new SolicitudInvalidaException(
                    "El rol ADMIN solo puede crearse mediante el bootstrap seguro");
        }

        Usuario usuario = buscar(id);
        usuario.setRol(request.rol());

        if (usuario.getEstado() == EstadoUsuario.PENDIENTE) {
            usuario.setEstado(EstadoUsuario.ACTIVO);
        }

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, EstadoRequest request, Long adminId) {
        if (request.estado() == EstadoUsuario.PENDIENTE) {
            throw new SolicitudInvalidaException(
                    "El estado PENDIENTE solo se asigna al registrar una cuenta");
        }

        Usuario usuario = buscar(id);

        if (request.estado() == EstadoUsuario.ACTIVO && usuario.getRol() == null) {
            throw new SolicitudInvalidaException(
                    "Asigna un rol antes de activar la cuenta");
        }

        if (request.estado() == EstadoUsuario.BLOQUEADO) {
            if (usuario.getId().equals(adminId)) {
                throw new SolicitudInvalidaException(
                        "No puedes bloquear tu propia cuenta");
            }

            if (usuario.getRol() == Rol.ADMIN) {
                long otrosAdminsActivos = usuarioRepository
                        .countByRolAndEstadoAndIdNot(
                                Rol.ADMIN, EstadoUsuario.ACTIVO, usuario.getId());

                if (otrosAdminsActivos == 0) {
                    throw new OperacionUsuarioNoPermitidaException(
                            "Debe existir al menos un ADMIN activo en el sistema");
                }
            }
        }

        usuario.setEstado(request.estado());
        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }
}
