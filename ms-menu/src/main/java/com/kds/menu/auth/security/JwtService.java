package com.kds.menu.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.kds.menu.auth.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

/**
 * Emite y valida JWT HS256. El secreto es compartido por los tres servicios,
 * pero solo ms-menu emite tokens porque solo él conoce los usuarios.
 */
@Component
public class JwtService {

    public static final String EMISOR = "kds-auth";

    private final SecretKey clave;
    private final long expiracionSegundos;

    public JwtService(
            @Value("${kds.security.jwt.secret}") String secret,
            @Value("${kds.security.jwt.expiration-minutes:30}") long expiracionMinutos) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 32 bytes para HS256");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException(
                    "kds.security.jwt.expiration-minutes debe ser mayor que cero");
        }

        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.expiracionSegundos = expiracionMinutos * 60;
    }

    public String emitir(Usuario usuario) {
        Instant ahora = Instant.now();

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject(usuario.getEmail())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .claim("userId", usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("estado", usuario.getEstado().name());

        if (usuario.getRol() != null) {
            claims.claim("role", usuario.getRol().name());
        }

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims.build());
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token JWT", ex);
        }
    }

    public JWTClaimsSet validar(String token) throws ParseException, JOSEException {
        SignedJWT jwt = SignedJWT.parse(token);

        if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new JOSEException("Algoritmo JWT no permitido");
        }

        if (!jwt.verify(new MACVerifier(clave))) {
            throw new JOSEException("Firma JWT inválida");
        }

        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        if (!EMISOR.equals(claims.getIssuer())) {
            throw new JOSEException("Emisor JWT inválido");
        }

        Date expiracion = claims.getExpirationTime();
        if (expiracion == null || expiracion.before(new Date())) {
            throw new JOSEException("Token JWT expirado");
        }

        if (claims.getLongClaim("userId") == null
                || claims.getStringClaim("email") == null) {
            throw new JOSEException("Token JWT incompleto");
        }

        return claims;
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }
}
