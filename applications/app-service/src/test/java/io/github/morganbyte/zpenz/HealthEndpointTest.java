package io.github.morganbyte.zpenz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arranca la aplicación completa, con PostgreSQL, en un puerto aleatorio y verifica el
 * criterio de cierre de la Fase 0: {@code /actuator/health} responde UP. Desde la Fase 2
 * ese estado incluye la base de datos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "RENDER_GIT_COMMIT=abc1234")
@Import(PostgresTestConfiguration.class)
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthEndpointReportsUp() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    void readinessProbeIsExposed() throws IOException, InterruptedException {
        assertEquals(200, get("/actuator/health/readiness").statusCode());
    }

    @Test
    void infoEndpointReportsTheDeployedCommit() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/info");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"commit\":\"abc1234\""), response.body());
    }

    @Test
    void metricsEndpointIsNotPublic() throws IOException, InterruptedException {
        assertEquals(404, get("/actuator/prometheus").statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
