package com.kds.cocina.security;

import com.kds.cocina.controller.OrdenCocinaController;
import com.kds.cocina.service.OrdenCocinaService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que ms-display-cocina valida el JWT emitido por ms-menu y que
 * solo ADMIN y COCINA operan el tablero de comandas.
 */
@WebMvcTest(controllers = OrdenCocinaController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "kds.security.jwt.secret=kds-secreto-de-pruebas-ms-cocina-2026-largo",
        "kds.cors.allowed-origin-patterns=*"
})
class OrdenesCocinaSecurityTest {

    private static final String SECRETO =
            "kds-secreto-de-pruebas-ms-cocina-2026-largo";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrdenCocinaService ordenCocinaService;

    @Test
    void sinToken_devuelve401ConMensajeDeNoAutenticado() throws Exception {

        mockMvc.perform(get("/ordenes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No autenticado o token inválido"));
    }

    @Test
    void tokenInvalido_devuelve401() throws Exception {

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer token-que-no-es-valido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpirado_devuelve401() throws Exception {

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar("COCINA", -60)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cocina_puedeConsultarElTablero() throws Exception {

        when(ordenCocinaService.listarOrdenes()).thenReturn(List.of());

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar("COCINA", 30)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_puedeConsultarElTablero() throws Exception {

        when(ordenCocinaService.listarOrdenes()).thenReturn(List.of());

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar("ADMIN", 30)))
                .andExpect(status().isOk());
    }

    @Test
    void cajaNoPuedeConsultarElTableroDeCocina() throws Exception {

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar("CAJA", 30)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tienes permisos para esta operación"));
    }

    @Test
    void gestorDeMenuNoPuedeConsultarElTableroDeCocina() throws Exception {

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar("GESTOR_MENU", 30)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cuentaPendienteSinRolNoPuedeConsultarElTablero() throws Exception {

        mockMvc.perform(get("/ordenes")
                        .header("Authorization", "Bearer " + firmar(null, 30)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cajaNoPuedeCambiarElEstadoDeUnaComanda() throws Exception {

        mockMvc.perform(patch("/ordenes/1/estado")
                        .header("Authorization", "Bearer " + firmar("CAJA", 30))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "PREPARANDO"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String firmar(String rol, int minutosValidez) {
        try {
            Date ahora = new Date();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(JwtService.EMISOR)
                    .subject("cocina@kds.local")
                    .issueTime(ahora)
                    .expirationTime(new Date(ahora.getTime() + minutosValidez * 60_000L))
                    .claim("userId", 1L)
                    .claim("email", "cocina@kds.local")
                    .claim("estado", "ACTIVO");

            if (rol != null) {
                claims.claim("role", rol);
            }

            SecretKey clave = new SecretKeySpec(
                    SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", exception);
        }
    }
}
