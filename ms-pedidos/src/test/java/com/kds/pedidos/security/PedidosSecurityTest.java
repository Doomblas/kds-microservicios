package com.kds.pedidos.security;

import com.kds.pedidos.controller.PedidoController;
import com.kds.pedidos.service.PedidoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que ms-pedidos valida el JWT emitido por ms-menu y que aplica los
 * roles por modulo: solo ADMIN y CAJA operan la caja.
 */
@WebMvcTest(controllers = PedidoController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "kds.security.jwt.secret=kds-secreto-de-pruebas-ms-pedidos-2026-largo",
        "kds.cors.allowed-origin-patterns=*"
})
class PedidosSecurityTest {

    private static final String SECRETO =
            "kds-secreto-de-pruebas-ms-pedidos-2026-largo";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @Test
    void sinToken_devuelve401ConMensajeDeNoAutenticado() throws Exception {

        mockMvc.perform(get("/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No autenticado o token inválido"));
    }

    @Test
    void tokenInvalido_devuelve401() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer token-que-no-es-valido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoConOtroSecreto_devuelve401() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + tokenConSecreto(
                                "otro-secreto-que-no-coincide-con-el-del-servicio-2026",
                                "CAJA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpirado_devuelve401() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + firmar(SECRETO, "CAJA", -60)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void caja_puedeListarPedidos() throws Exception {

        when(pedidoService.listarPedidos()).thenReturn(List.of());

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + token("CAJA")))
                .andExpect(status().isOk());
    }

    @Test
    void admin_puedeListarPedidos() throws Exception {

        when(pedidoService.listarPedidos()).thenReturn(List.of());

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cocinaNoPuedeOperarLaCaja() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + token("COCINA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tienes permisos para esta operación"));
    }

    @Test
    void gestorDeMenuNoPuedeOperarLaCaja() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + token("GESTOR_MENU")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cuentaPendienteSinRolNoPuedeOperarLaCaja() throws Exception {

        mockMvc.perform(get("/pedidos")
                        .header("Authorization", "Bearer " + tokenSinRol()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cocinaNoPuedeCrearPedidos() throws Exception {

        mockMvc.perform(post("/pedidos")
                        .header("Authorization", "Bearer " + token("COCINA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "productoId": 1,
                                      "cantidad": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String token(String rol) {
        return tokenConSecreto(SECRETO, rol);
    }

    private String tokenSinRol() {
        return firmar(SECRETO, null, 30);
    }

    private String tokenConSecreto(String secreto, String rol) {
        return firmar(secreto, rol, 30);
    }

    private String firmar(String secreto, String rol, int minutosValidez) {
        try {
            Date ahora = new Date();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(JwtService.EMISOR)
                    .subject("usuario@kds.local")
                    .issueTime(ahora)
                    .expirationTime(new Date(ahora.getTime() + minutosValidez * 60_000L))
                    .claim("userId", 1L)
                    .claim("email", "usuario@kds.local")
                    .claim("estado", "ACTIVO");

            if (rol != null) {
                claims.claim("role", rol);
            }

            SecretKey clave = new SecretKeySpec(
                    secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", exception);
        }
    }
}
