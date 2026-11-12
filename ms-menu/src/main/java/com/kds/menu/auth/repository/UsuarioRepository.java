package com.kds.menu.auth.repository;

import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRol(Rol rol);

    List<Usuario> findAllByOrderByFechaCreacionDesc();

    List<Usuario> findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByFechaCreacionDesc(
            String nombre,
            String email);

    long countByRolAndEstadoAndIdNot(Rol rol, EstadoUsuario estado, Long id);
}
