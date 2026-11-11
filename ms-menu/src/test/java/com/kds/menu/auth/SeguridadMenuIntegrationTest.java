package com.kds.menu.auth;

import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;
import com.kds.menu.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de la autorización real de ms-menu con la cadena de seguridad
 * completa (Spring Security + JWT + BCrypt) sobre una base en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:menu_auth_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "kds.security.jwt.secret=kds-secreto-de-pruebas-integracion-menu-2026-largo",
        "kds.security.jwt.expiration-minutes=30",
        "kds.admin.email=",
        "kds.admin.password="
})
class SeguridadMenuIntegrationTest {

    private static final String CLAVE = "ClaveSegura123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void limpiarCuentas() {
        usuarioRepository.deleteAll();
    }

    @Test
    void registrar_creaCuentaPendienteSinRolYSinExponerCredenciales() throws Exception {

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Cuenta Nueva",
                                  "email": "nueva@kds.local",
                                  "password": "ClaveSegura123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Cuenta Nueva"))
                .andExpect(jsonPath("$.email").value("nueva@kds.local"))
                .andExpect(jsonPath("$.rol").value(nullValue()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        Usuario guardado = usuarioRepository.findByEmailIgnoreCase("nueva@kds.local").orElseThrow();
        assertThat(guardado.getRol()).isNull();
        assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
        assertThat(guardado.getPasswordHash()).isNotEqualTo(CLAVE);
        assertThat(passwordEncoder.matches(CLAVE, guardado.getPasswordHash())).isTrue();
    }

    @Test
    void registrar_ignoraElRolEnviadoPorElFrontend() throws Exception {

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Intruso",
                                  "email": "intruso@kds.local",
                                  "password": "ClaveSegura123",
                                  "rol": "ADMIN",
                                  "estado": "ACTIVO"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value(nullValue()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void registrar_rechazaCorreoDuplicado() throws Exception {

        crearUsuario("Duplicado", "dup@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Duplicado",
                                  "email": "dup@kds.local",
                                  "password": "ClaveSegura123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void login_sinTokenNoPermiteConsultarLaSesion() throws Exception {

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_devuelveTokenYElUsuarioActual() throws Exception {

        crearUsuario("Caja KDS", "caja@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);

        String token = tokenDe("caja@kds.local", CLAVE);

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("caja@kds.local"))
                .andExpect(jsonPath("$.rol").value("CAJA"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void login_rechazaContrasenaIncorrecta() throws Exception {

        crearUsuario("Caja KDS", "caja@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "caja@kds.local",
                                  "password": "clave-equivocada"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_rechazaCuentaBloqueada() throws Exception {

        crearUsuario("Bloqueada", "bloqueada@kds.local", Rol.COCINA, EstadoUsuario.BLOQUEADO);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bloqueada@kds.local",
                                  "password": "ClaveSegura123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void cuentaPendiente_autenticaPeroNoEntraAlMenu() throws Exception {

        crearUsuario("Pendiente", "pendiente@kds.local", null, EstadoUsuario.PENDIENTE);

        String token = tokenDe("pendiente@kds.local", CLAVE);

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void caja_leeElCatalogoPeroNoPuedeModificarlo() throws Exception {

        crearUsuario("Caja KDS", "caja@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);
        String token = tokenDe("caja@kds.local", CLAVE);

        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Categoria No Autorizada",
                                  "activo": true
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Producto No Autorizado",
                                  "precio": 4.50,
                                  "disponible": true,
                                  "categoriaId": 1
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorMenu_modificaElMenu() throws Exception {

        crearUsuario("Gestor", "gestor@kds.local", Rol.GESTOR_MENU, EstadoUsuario.ACTIVO);
        String token = tokenDe("gestor@kds.local", CLAVE);

        mockMvc.perform(post("/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Entradas",
                                  "activo": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Entradas"));
    }

    @Test
    void cocinaNoAccedeAlMenu() throws Exception {

        crearUsuario("Cocina", "cocina@kds.local", Rol.COCINA, EstadoUsuario.ACTIVO);
        String token = tokenDe("cocina@kds.local", CLAVE);

        mockMvc.perform(get("/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloAdminGestionaUsuarios() throws Exception {

        crearUsuario("Admin", "admin@kds.local", Rol.ADMIN, EstadoUsuario.ACTIVO);
        crearUsuario("Caja", "caja@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);

        String tokenAdmin = tokenDe("admin@kds.local", CLAVE);
        String tokenCaja = tokenDe("caja@kds.local", CLAVE);

        mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists());

        mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + tokenCaja))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/usuarios/1/estado")
                        .header("Authorization", "Bearer " + tokenCaja)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "BLOQUEADO"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAsignaRolYActivaLaCuentaPendiente() throws Exception {

        Usuario admin = crearUsuario("Admin", "admin@kds.local", Rol.ADMIN, EstadoUsuario.ACTIVO);
        Usuario pendiente = crearUsuario("Nueva", "nueva@kds.local", null, EstadoUsuario.PENDIENTE);

        String token = tokenDe("admin@kds.local", CLAVE);

        mockMvc.perform(patch("/usuarios/" + pendiente.getId() + "/rol")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rol": "COCINA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("COCINA"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        mockMvc.perform(patch("/usuarios/" + admin.getId() + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "BLOQUEADO"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No puedes bloquear tu propia cuenta"));
    }

    @Test
    void adminNoPuedeAsignarElRolAdminNiBloquearAlUltimoAdmin() throws Exception {

        Usuario admin = crearUsuario("Admin", "admin@kds.local", Rol.ADMIN, EstadoUsuario.ACTIVO);
        Usuario pendiente = crearUsuario("Nueva", "nueva@kds.local", null, EstadoUsuario.PENDIENTE);

        String token = tokenDe("admin@kds.local", CLAVE);

        mockMvc.perform(patch("/usuarios/" + pendiente.getId() + "/rol")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rol": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El rol ADMIN solo puede crearse mediante el bootstrap seguro"));

        mockMvc.perform(patch("/usuarios/" + admin.getId() + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "BLOQUEADO"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminBloqueaYReactivaUnaCuentaConRol() throws Exception {

        crearUsuario("Admin", "admin@kds.local", Rol.ADMIN, EstadoUsuario.ACTIVO);
        Usuario cocina = crearUsuario("Cocina", "cocina@kds.local", Rol.COCINA, EstadoUsuario.ACTIVO);

        String token = tokenDe("admin@kds.local", CLAVE);

        mockMvc.perform(patch("/usuarios/" + cocina.getId() + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "BLOQUEADO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));

        mockMvc.perform(patch("/usuarios/" + cocina.getId() + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "ACTIVO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void listarUsuariosPermiteBuscarPorNombreOEmail() throws Exception {

        crearUsuario("Admin", "admin@kds.local", Rol.ADMIN, EstadoUsuario.ACTIVO);
        crearUsuario("Caja KDS", "caja@kds.local", Rol.CAJA, EstadoUsuario.ACTIVO);

        String token = tokenDe("admin@kds.local", CLAVE);

        mockMvc.perform(get("/usuarios").param("q", "caja@kds.local")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("caja@kds.local"));
    }

    private Usuario crearUsuario(
            String nombre,
            String email,
            Rol rol,
            EstadoUsuario estado) {

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario.setRol(rol);
        usuario.setEstado(estado);
        return usuarioRepository.save(usuario);
    }

    private String tokenDe(String email, String clave) throws Exception {

        String respuesta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", clave))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(respuesta).get("token").asText();
    }
}
