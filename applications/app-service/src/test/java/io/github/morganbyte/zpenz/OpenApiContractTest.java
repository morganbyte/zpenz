package io.github.morganbyte.zpenz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato de la API guardado en {@code docs/api/openapi.yaml} es el que genera el código
 * (ADR-0009). Si un cambio altera la API, esta prueba falla hasta que se actualice el archivo:
 *
 * <pre>UPDATE_OPENAPI=true ./gradlew :app-service:test --tests '*OpenApiContractTest' --rerun</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class OpenApiContractTest {

    /** Las pruebas de Gradle corren desde la carpeta del módulo. */
    private static final Path CONTRACT = Path.of("../../docs/api/openapi.yaml");

    @LocalServerPort
    private int port;

    @Test
    void theStoredContractMatchesTheCode() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs.yaml")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        String generated = response.body().replace("\r\n", "\n");

        if ("true".equals(System.getenv("UPDATE_OPENAPI"))) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, generated, StandardCharsets.UTF_8);
        }

        assertThat(CONTRACT).as("Falta %s: genérelo con UPDATE_OPENAPI=true", CONTRACT).exists();
        assertThat(generated)
                .as("La API cambió. Revise el cambio y actualice %s con UPDATE_OPENAPI=true", CONTRACT)
                .isEqualTo(Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}
