package io.github.morganbyte.zpenz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las rutas de la Fase 3 por HTTP, con la aplicación completa y PostgreSQL real (ADR-0008).
 * Las pruebas de concurrencia lanzan las peticiones a la vez desde varios hilos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class TransferEndToEndTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @LocalServerPort
    private int port;

    @Value("${zpenz.api-token}")
    private String token;

    private final HttpClient client = HttpClient.newHttpClient();

    private String savings;
    private String cash;
    private String opening;

    @BeforeEach
    void openAccounts() throws Exception {
        savings = createAccount("Cuenta de ahorros", "ASSET");
        cash = createAccount("Efectivo", "ASSET");
        opening = createAccount("Saldo inicial", "EQUITY");
        // El patrimonio no tiene límite: así se fondea la cuenta de ahorros.
        assertThat(transfer(UUID.randomUUID().toString(), opening, savings, "100000").statusCode()).isEqualTo(201);
    }

    @Test
    void transfersAndReportsBalances() throws Exception {
        // El monto se acepta como número o como texto JSON.
        HttpResponse<String> response = transfer(UUID.randomUUID().toString(), savings, cash, "\"70000.50\"");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Idempotent-Replayed")).contains("false");
        assertThat(response.body()).contains("\"from\":\"" + savings + "\"", "\"to\":\"" + cash + "\"",
                "\"amount\":\"70000.50\"");
        assertThat(balance(savings)).isEqualTo("29999.50");
        assertThat(balance(cash)).isEqualTo("70000.50");
    }

    @Test
    void concurrentTransfersNeverOverdrawAnAssetAccount() throws Exception {
        // 100 000 alcanza para 3 transferencias de 30 000, no para 10.
        List<HttpResponse<String>> responses = concurrently(10,
                () -> transfer(UUID.randomUUID().toString(), savings, cash, "30000"));

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(3);
        assertThat(responses).filteredOn(r -> r.statusCode() == 422)
                .hasSize(7)
                .allSatisfy(r -> assertThat(r.body()).contains("\"code\":\"insufficient-funds\""));
        assertThat(balance(savings)).isEqualTo("10000.00");
        assertThat(balance(cash)).isEqualTo("90000.00");
    }

    @Test
    void concurrentRetriesWithTheSameKeyRecordOneTransfer() throws Exception {
        String key = UUID.randomUUID().toString();

        List<HttpResponse<String>> responses = concurrently(8, () -> transfer(key, savings, cash, "1000"));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        assertThat(responses.stream().map(r -> idOf(r.body())).distinct()).hasSize(1);
        assertThat(responses).filteredOn(r -> r.headers().firstValue("Idempotent-Replayed").orElseThrow().equals("false"))
                .hasSize(1);
        assertThat(balance(cash)).isEqualTo("1000.00");
    }

    @Test
    void rejectsReusingAKeyForAnotherTransfer() throws Exception {
        String key = UUID.randomUUID().toString();
        transfer(key, savings, cash, "1000");

        HttpResponse<String> response = transfer(key, savings, cash, "2000");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"idempotency-key-reused\"");
        assertThat(balance(cash)).isEqualTo("1000.00");
    }

    @Test
    void reportsClientErrors() throws Exception {
        String unknown = UUID.randomUUID().toString();
        assertThat(transfer(UUID.randomUUID().toString(), savings, unknown, "1").statusCode()).isEqualTo(404);
        assertThat(transfer(UUID.randomUUID().toString(), savings, savings, "1").statusCode()).isEqualTo(400);
        assertThat(transfer(UUID.randomUUID().toString(), savings, cash, "0.001").statusCode()).isEqualTo(400);
        assertThat(transfer(null, savings, cash, "1").statusCode()).isEqualTo(400);
        assertThat(transfer("con espacio", savings, cash, "1").statusCode()).isEqualTo(400);
        assertThat(send(post("/transfers", "{\"from\":\"" + savings + "\"}")
                .header("Idempotency-Key", UUID.randomUUID().toString())).body()).contains("Falta el campo");
        assertThat(send(post("/transfers", "no es json")
                .header("Idempotency-Key", UUID.randomUUID().toString())).statusCode()).isEqualTo(400);
        assertThat(send(post("/accounts", "{\"name\":\"X\",\"type\":\"OTRO\"}")).statusCode()).isEqualTo(400);
        assertThat(send(get("/accounts/" + unknown + "/balance")).statusCode()).isEqualTo(404);
        assertThat(send(get("/accounts/no-es-uuid/balance")).statusCode()).isEqualTo(400);
        assertThat(balance(savings)).isEqualTo("100000.00");
    }

    @Test
    void requiresTheApiToken() throws Exception {
        HttpRequest withoutToken = HttpRequest.newBuilder(uri("/accounts/" + savings + "/balance")).GET().build();
        assertThat(client.send(withoutToken, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    private String createAccount(String name, String type) throws Exception {
        HttpResponse<String> response = send(post("/accounts",
                "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"));
        assertThat(response.statusCode()).isEqualTo(201);
        return idOf(response.body());
    }

    private HttpResponse<String> transfer(String key, String from, String to, String amount) throws Exception {
        HttpRequest.Builder request = post("/transfers", """
                {"from":"%s","to":"%s","amount":%s,"date":"2026-11-28","description":"Transferencia"}"""
                .formatted(from, to, amount));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return send(request);
    }

    private String balance(String account) throws Exception {
        HttpResponse<String> response = send(get("/accounts/" + account + "/balance"));
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher m = Pattern.compile("\"balance\":\"([-0-9.]+)\"").matcher(response.body());
        assertThat(m.find()).as(response.body()).isTrue();
        return m.group(1);
    }

    private static String idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    private <T> List<T> concurrently(int threads, Callable<T> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest.Builder post(String path, String json) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.header("Authorization", "Bearer " + token).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
