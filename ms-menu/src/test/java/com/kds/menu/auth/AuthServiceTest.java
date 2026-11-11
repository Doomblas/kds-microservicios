package com.kds.menu.auth;

import com.kds.menu.auth.dto.AuthResponse;
import com.kds.menu.auth.dto.RegistroRequest;
import com.kds.menu.auth.dto.UsuarioResponse;
import com.kds.menu.auth.exception.CredencialesInvalidasException;
import com.kds.menu.auth.exception.CuentaBloqueadaException;
import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;
import com.kds.menu.auth.repository.UsuarioRepository;
import com.kds.menu.auth.security.JwtService;
import com.kds.menu.auth.service.AuthService;
import com.kds.menu.exception.RecursoDuplicadoException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.ParseException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unidad del servicio de autenticación: hashing BCrypt, normalización del
 * correo, emisión del JWT y control de cuentas bloqueadas.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRETO =
            "kds-secreto-de-pruebas-autenticacion-2026-suficientemente-largo";
    private static final String CLAVE = "ClaveSegura123";

    @Mock
    private UsuarioRepository usuarioRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                usuarioRepository,
                passwordEncoder,
                new JwtService(SECRETO, 30));
    }

    @Test
    void registrar_guardaHashBcryptSinRolYEnEstadoPendiente() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@kds.local")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            usuario.setId(7L);
            return usuario;
        });

        UsuarioResponse respuesta = authService.registrar(
                new RegistroRequest("Nueva Cuenta", "nuevo@kds.local", CLAVE));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertThat(guardado.getPasswordHash()).isNotEqualTo(CLAVE);
        assertThat(guardado.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(CLAVE, guardado.getPasswordHash())).isTrue();
        assertThat(guardado.getRol()).isNull();
        assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
        assertThat(guardado.getEmail()).isEqualTo("nuevo@kds.local");
        assertThat(respuesta.rol()).isNull();
        assertThat(respuesta.estado()).isEqualTo(EstadoUsuario.PENDIENTE);
    }

    @Test
    void registrar_rechazaCorreoDuplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@kds.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("Nueva Cuenta", "nuevo@kds.local", CLAVE)))
                .isInstanceOf(RecursoDuplicadoException.class)
                .hasMessageContaining("nuevo@kds.local");
    }

    @Test
    void registrar_normalizaElCorreoAPMinusculas() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@kds.local")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        authService.registrar(new RegistroRequest("Nueva Cuenta", "  NUEVO@KDS.LOCAL ", CLAVE));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nuevo@kds.local");
        assertThat(captor.getValue().getNombre()).isEqualTo("Nueva Cuenta");
    }

    @Test
    void login_emiteTokenConLosClaimsDelUsuario() throws ParseException {
        Usuario usuario = usuarioActivo(Rol.COCINA, 42L);
        when(usuarioRepository.findByEmailIgnoreCase("cocina@kds.local"))
                .thenReturn(Optional.of(usuario));

        AuthResponse respuesta = authService.login(
                new com.kds.menu.auth.dto.LoginRequest("COCINA@KDS.LOCAL", CLAVE));

        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(1800L);
        assertThat(respuesta.usuario().rol()).isEqualTo(Rol.COCINA);
        assertThat(respuesta.usuario().estado()).isEqualTo(EstadoUsuario.ACTIVO);

        JWTClaimsSet claims = SignedJWT.parse(respuesta.token()).getJWTClaimsSet();
        assertThat(claims.getLongClaim("userId")).isEqualTo(42L);
        assertThat(claims.getStringClaim("email")).isEqualTo("cocina@kds.local");
        assertThat(claims.getStringClaim("role")).isEqualTo("COCINA");
        assertThat(claims.getStringClaim("estado")).isEqualTo("ACTIVO");
        assertThat(claims.getIssuer()).isEqualTo(JwtService.EMISOR);
    }

    @Test
    void login_omiteElClaimDeRolCuandoLaCuentaNoTieneRol() throws ParseException {
        Usuario usuario = usuarioPendiente(9L);
        when(usuarioRepository.findByEmailIgnoreCase("pendiente@kds.local"))
                .thenReturn(Optional.of(usuario));

        AuthResponse respuesta = authService.login(
                new com.kds.menu.auth.dto.LoginRequest("pendiente@kds.local", CLAVE));

        JWTClaimsSet claims = SignedJWT.parse(respuesta.token()).getJWTClaimsSet();
        assertThat(claims.getStringClaim("role")).isNull();
        assertThat(claims.getStringClaim("estado")).isEqualTo("PENDIENTE");
    }

    @Test
    void login_fallaConCredencialesIncorrectas() {
        Usuario usuario = usuarioActivo(Rol.CAJA, 3L);
        when(usuarioRepository.findByEmailIgnoreCase("caja@kds.local"))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(
                new com.kds.menu.auth.dto.LoginRequest("caja@kds.local", "otra-clave")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void login_rechazaCuentasBloqueadas() {
        Usuario usuario = usuarioActivo(Rol.CAJA, 4L);
        usuario.setEstado(EstadoUsuario.BLOQUEADO);
        when(usuarioRepository.findByEmailIgnoreCase("caja@kds.local"))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(
                new com.kds.menu.auth.dto.LoginRequest("caja@kds.local", CLAVE)))
                .isInstanceOf(CuentaBloqueadaException.class);
    }

    private Usuario usuarioActivo(Rol rol, Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + rol.name());
        usuario.setEmail(rol.name().toLowerCase() + "@kds.local");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario.setRol(rol);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        return usuario;
    }

    private Usuario usuarioPendiente(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Cuenta Nueva");
        usuario.setEmail("pendiente@kds.local");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario.setRol(null);
        usuario.setEstado(EstadoUsuario.PENDIENTE);
        return usuario;
    }
}
