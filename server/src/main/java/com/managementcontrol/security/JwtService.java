package com.managementcontrol.security;

import com.managementcontrol.config.ManagementControlProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Видача та перевірка JWT для адміністраторів панелі керування. */
@Service
public class JwtService {

    private final SecretKey key;
    private final ManagementControlProperties properties;

    public JwtService(ManagementControlProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("MC_JWT_SECRET має містити щонайменше 32 байти");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(AdminPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(principal.getUsername())
                .claim("role", principal.getRole().name())
                .claim("uid", principal.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getJwt().getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
