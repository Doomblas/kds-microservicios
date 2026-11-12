package com.kds.menu.auth.service;

import com.kds.menu.auth.dto.AuthResponse;
import com.kds.menu.auth.dto.LoginRequest;
import com.kds.menu.auth.dto.RegistroRequest;
import com.kds.menu.auth.dto.UsuarioResponse;
import com.kds.menu.auth.exception.CredencialesInvalidasException;
import com.kds.menu.auth.exception.CuentaBloqueadaException;
import com.kds.menu.auth.exception.NoAutenticadoException;
import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Usuario;
import com.kds.menu.auth.repository.UsuarioRepository;
import com.kds.menu.auth.security.JwtService;
import com.kds.menu.exception.RecursoDuplicadoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String email = normalizarEmail(request.email());

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RecursoDuplicadoException(
                    "Ya existe una cuenta registrada con el correo: " + email);
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setRol(null);
        usuario.setEstado(EstadoUsuario.PENDIENTE);

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository
                .findByEmailIgnoreCase(normalizarEmail(request.email()))
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        if (usuario.getEstado() == EstadoUsuario.BLOQUEADO) {
            throw new CuentaBloqueadaException();
        }

        String token = jwtService.emitir(usuario);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpiracionSegundos(),
                UsuarioResponse.desde(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerActual(Long userId) {
        return usuarioRepository.findById(userId)
                .map(UsuarioResponse::desde)
                .orElseThrow(NoAutenticadoException::new);
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
