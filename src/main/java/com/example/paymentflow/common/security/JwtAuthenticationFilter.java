package com.example.paymentflow.common.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);


    @Value("${app.jwt.issuer:payment-reconciliation-service}")
    private String jwtIssuer;

    @Value("${app.jwt.audience:payment-reconciliation-api}")
    private String jwtAudience;

    @Value("${app.jwt.secret:dummy}")
    private String jwtSecret;

    @jakarta.annotation.PostConstruct
    public void logSecretLength() {
        log.info("JwtAuthenticationFilter bean created. (jwtSecret length: {}, jwtIssuer: {}, jwtAudience: {})",
            jwtSecret != null ? jwtSecret.length() : "null",
            jwtIssuer,
            jwtAudience);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("JwtAuthenticationFilter: doFilterInternal called for URI: {}", request.getRequestURI());
        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt) && validateToken(jwt)) {
            Claims claims = getClaims(jwt);
            String username = claims.getSubject();
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims getClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSecretSigningKey())
        .requireIssuer(jwtIssuer)
        .requireAudience(jwtAudience)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    }


    private static javax.crypto.SecretKey getSecretSigningKey(String secret) {
        try {
            // Try hex decoding first
            byte[] keyBytes = hexStringToByteArray(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            // Fallback to base64 decoding
            try {
                byte[] keyBytes = Decoders.BASE64.decode(secret);
                return Keys.hmacShaKeyFor(keyBytes);
            } catch (Exception ex) {
                // Use string directly if both fail
                return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private javax.crypto.SecretKey getSecretSigningKey() {
        return getSecretSigningKey(jwtSecret);
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
