package com.kds.cocina.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;

/**
 * Validación local y stateless del JWT emitido por ms-menu. Cocina no conoce
 * usuarios ni accede a la base de datos de menú.
 */
@Component
public class JwtService {

    public static final String EMISOR = "kds-auth";

    private final SecretKey clave;

    public JwtService(@Value("${kds.security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 32 bytes para HS256");
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
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
}
