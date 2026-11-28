package io.github.morganbyte.zpenz.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@WebFilter("/**")
public class SecurityHeadersFilter implements Filter {

    static final String DEFAULT_POLICY = "default-src 'self'; frame-ancestors 'self'; form-action 'self'";

    /**
     * Swagger UI usa estilos en línea e íconos {@code data:} (ADR-0009). Solo en sus rutas; los
     * scripts siguen limitados al propio origen.
     */
    static final String SWAGGER_UI_POLICY = "default-src 'self'; img-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; frame-ancestors 'self'; form-action 'self'";

    private static boolean isSwaggerUi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/swagger-ui");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse header = (HttpServletResponse) response;
        header.setHeader("Content-Security-Policy",
                isSwaggerUi((HttpServletRequest) request) ? SWAGGER_UI_POLICY : DEFAULT_POLICY);
        header.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        header.setHeader("X-Content-Type-Options", "nosniff");
        header.setHeader("Server", "");
        header.setHeader("Cache-Control", "no-store");
        header.setHeader("Pragma", "no-cache");
        header.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        chain.doFilter(request, response);
    }
}
