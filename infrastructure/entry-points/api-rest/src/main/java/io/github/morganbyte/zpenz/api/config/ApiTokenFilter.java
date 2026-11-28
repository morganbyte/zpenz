package io.github.morganbyte.zpenz.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Token compartido para la API hasta que llegue JWT en la Fase 5 (ADR-0008). Todo lo que está
 * bajo {@code /api/} exige {@code Authorization: Bearer <API_TOKEN>}. Actuator, Swagger UI y la
 * especificación OpenAPI son públicos (ADR-0009).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiTokenFilter extends OncePerRequestFilter {

    static final int MIN_TOKEN_LENGTH = 32;
    private static final String BEARER = "Bearer ";

    private final byte[] expected;

    public ApiTokenFilter(@Value("${zpenz.api-token}") String token) {
        if (token == null || token.strip().length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "API_TOKEN debe tener al menos " + MIN_TOKEN_LENGTH + " caracteres (ADR-0008)");
        }
        this.expected = token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (authorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"Falta el token de la API o no es válido"}""");
    }

    private boolean authorized(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            return false;
        }
        byte[] given = header.substring(BEARER.length()).strip().getBytes(StandardCharsets.UTF_8);
        // Comparación en tiempo constante: no revela cuántos caracteres coinciden.
        return MessageDigest.isEqual(expected, given);
    }
}
