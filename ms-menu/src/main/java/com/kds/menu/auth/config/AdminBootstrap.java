package com.kds.menu.auth.config;

import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;
import com.kds.menu.auth.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Crea el primer ADMIN solo si no existe ninguno. Las credenciales vienen de
 * variables de entorno y nunca se escriben en logs ni en el repositorio.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public AdminBootstrap(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${kds.admin.email:}") String adminEmail,
            @Value("${kds.admin.password:}") String adminPassword,
            @Value("${kds.admin.name:Administrador KDS}") String adminName) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("Bootstrap de ADMIN omitido: define ADMIN_EMAIL y ADMIN_PASSWORD para crearlo");
            return;
        }

        if (adminPassword.length() < 8) {
            log.error("Bootstrap de ADMIN omitido: ADMIN_PASSWORD debe tener al menos 8 caracteres");
            return;
        }

        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            log.info("Bootstrap de ADMIN omitido: ya existe una cuenta ADMIN");
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Bootstrap de ADMIN omitido: ya existe una cuenta con el correo configurado");
            return;
        }

        Usuario admin = new Usuario();
        admin.setNombre(adminName == null || adminName.isBlank()
                ? "Administrador KDS"
                : adminName.trim());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRol(Rol.ADMIN);
        admin.setEstado(EstadoUsuario.ACTIVO);
        usuarioRepository.save(admin);

        log.info("ADMIN inicial creado para {}", email);
    }
}
