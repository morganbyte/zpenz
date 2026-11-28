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
 * La API v1 por HTTP, con la aplicación completa y PostgreSQL real (ADR-0008 y ADR-0009). Las
 * pruebas de concurrencia lanzan las peticiones a la vez desde varios hilos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class ApiV1EndToEndTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    private static final String DATE = "2026-11-28";

    @LocalServerPort
    private int port;

    @Value("${zpenz.api-token}")
    private String token;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private String savings;
    private String cash;
    private String opening;

    @BeforeEach
    void openAccounts() throws Exception {
        savings = createAccount("Cuenta de ahorros", "ASSET");
        cash = createAccount("Efectivo", "ASSET");
        opening = createAccount("Saldo inicial", "EQUITY");
        // El patrimonio no tiene límite: así se fondea la cuenta de ahorros.
        assertThat(transfer(newKey(), opening, savings, "100000").statusCode()).isEqualTo(201);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void transfersAndReportsAccountsWithTheirBalance() throws Exception {
        HttpResponse<String> response = transfer(newKey(), savings, cash, "70000.50");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Idempotent-Replayed")).contains("false");
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/journal-entries/" + idOf(response.body()));
        assertThat(response.body()).contains("\"from\":\"" + savings + "\"", "\"to\":\"" + cash + "\"",
                "\"amount\":\"70000.50\"");
        assertThat(balance(savings)).isEqualTo("29999.50");
        assertThat(balance(cash)).isEqualTo("70000.50");
        assertThat(send(get("/api/v1/accounts")).body()).contains("\"items\":[", "\"id\":\"" + savings + "\"");
    }

    @Test
    void creatingAnAccountIsIdempotent() throws Exception {
        String key = newKey();
        String body = "{\"name\":\"Tarjeta\",\"type\":\"LIABILITY\"}";

        HttpResponse<String> first = send(post("/api/v1/accounts", body).header("Idempotency-Key", key));
        HttpResponse<String> second = send(post("/api/v1/accounts", body).header("Idempotency-Key", key));
        HttpResponse<String> other = send(post("/api/v1/accounts", "{\"name\":\"Otra\",\"type\":\"LIABILITY\"}")
                .header("Idempotency-Key", key));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(idOf(second.body())).isEqualTo(idOf(first.body()));
        assertThat(other.statusCode()).isEqualTo(422);
        assertThat(other.body()).contains("\"code\":\"idempotency-key-reused\"");
    }

    @Test
    void postsReadsPagesAndReversesEntries() throws Exception {
        String groceries = createAccount("Mercado", "EXPENSE");
        String entryBody = """
                {"date":"%s","description":"Mercado","lines":[
                  {"accountId":"%s","side":"DEBIT","amount":"25000.00"},
                  {"accountId":"%s","side":"CREDIT","amount":"25000"}]}""".formatted(DATE, groceries, savings);
        String key = newKey();

        HttpResponse<String> posted = send(post("/api/v1/journal-entries", entryBody).header("Idempotency-Key", key));
        HttpResponse<String> repeated = send(post("/api/v1/journal-entries", entryBody).header("Idempotency-Key", key));
        String entry = idOf(posted.body());

        assertThat(posted.statusCode()).isEqualTo(201);
        assertThat(repeated.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(send(get("/api/v1/journal-entries/" + entry)).body())
                .contains("\"description\":\"Mercado\"", "\"side\":\"DEBIT\"", "\"amount\":\"25000.00\"", "\"reversalOf\":null");
        assertThat(balance(groceries)).isEqualTo("25000.00");

        String reversalBody = "{\"date\":\"" + DATE + "\",\"description\":\"Reverso: mercado duplicado\"}";
        HttpResponse<String> reversal = send(post("/api/v1/journal-entries/" + entry + "/reversal", reversalBody)
                .header("Idempotency-Key", newKey()));
        assertThat(reversal.statusCode()).isEqualTo(201);
        assertThat(reversal.body()).contains("\"reversalOf\":\"" + entry + "\"");
        assertThat(balance(groceries)).isEqualTo("0.00");

        HttpResponse<String> again = send(post("/api/v1/journal-entries/" + entry + "/reversal", reversalBody)
                .header("Idempotency-Key", newKey()));
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.body()).contains("\"code\":\"entry-already-reversed\"");
        HttpResponse<String> ofReversal = send(post("/api/v1/journal-entries/" + idOf(reversal.body()) + "/reversal",
                reversalBody).header("Idempotency-Key", newKey()));
        assertThat(ofReversal.statusCode()).isEqualTo(409);
        assertThat(ofReversal.body()).contains("\"code\":\"entry-is-reversal\"");

        // Asientos de la cuenta de gastos: el reverso y el original, del más reciente al más antiguo.
        HttpResponse<String> page1 = send(get("/api/v1/accounts/" + groceries + "/entries?limit=1"));
        Matcher cursor = Pattern.compile("\"nextCursor\":\"([^\"]+)\"").matcher(page1.body());
        assertThat(cursor.find()).as(page1.body()).isTrue();
        assertThat(idOf(page1.body())).isEqualTo(idOf(reversal.body()));
        HttpResponse<String> page2 = send(get("/api/v1/accounts/" + groceries + "/entries?limit=1&cursor=" + cursor.group(1)));
        assertThat(idOf(page2.body())).isEqualTo(entry);
        assertThat(page2.body()).contains("\"nextCursor\":null");
    }

    @Test
    void concurrentTransfersNeverOverdrawAnAssetAccount() throws Exception {
        // 100 000 alcanza para 3 transferencias de 30 000, no para 10.
        List<HttpResponse<String>> responses = concurrently(10, () -> transfer(newKey(), savings, cash, "30000"));

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(3);
        assertThat(responses).filteredOn(r -> r.statusCode() == 422)
                .hasSize(7)
                .allSatisfy(r -> assertThat(r.body()).contains("\"code\":\"insufficient-funds\""));
        assertThat(balance(savings)).isEqualTo("10000.00");
        assertThat(balance(cash)).isEqualTo("90000.00");
    }

    @Test
    void concurrentRetriesWithTheSameKeyRecordOneTransfer() throws Exception {
        String key = newKey();

        List<HttpResponse<String>> responses = concurrently(8, () -> transfer(key, savings, cash, "1000"));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        assertThat(responses.stream().map(r -> idOf(r.body())).distinct()).hasSize(1);
        assertThat(responses).filteredOn(r -> r.headers().firstValue("Idempotent-Replayed").orElseThrow().equals("false"))
                .hasSize(1);
        assertThat(balance(cash)).isEqualTo("1000.00");
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/transfers",
                "{\"from\":\"" + savings + "\",\"amount\":\"1.001\",\"description\":\" \"}")
                .header("Idempotency-Key", newKey()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"validation-failed\"",
                "\"field\":\"to\"", "\"field\":\"amount\"", "\"field\":\"date\"", "\"field\":\"description\"");
    }

    @Test
    void rejectsAmountsSentAsJsonNumbers() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/transfers", """
                {"from":"%s","to":"%s","amount":70000.5,"date":"%s","description":"Retiro"}"""
                .formatted(savings, cash, DATE)).header("Idempotency-Key", newKey()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"invalid-request\"");
        assertThat(balance(cash)).isEqualTo("0.00");
    }

    @Test
    void reportsClientErrorsWithACode() throws Exception {
        String unknown = UUID.randomUUID().toString();
        assertThat(transfer(newKey(), savings, unknown, "1").body()).contains("\"code\":\"account-not-found\"");
        assertThat(transfer(newKey(), savings, savings, "1").body()).contains("\"code\":\"invalid-request\"");
        assertThat(transfer(null, savings, cash, "1").body()).contains("\"code\":\"invalid-request\"");
        HttpResponse<String> badKey = transfer("con espacio", savings, cash, "1");
        assertThat(badKey.statusCode()).isEqualTo(400);
        assertThat(badKey.body()).contains("\"code\":\"validation-failed\"", "\"field\":\"Idempotency-Key\"");
        assertThat(send(post("/api/v1/accounts", "no es json").header("Idempotency-Key", newKey())).statusCode())
                .isEqualTo(400);
        assertThat(send(get("/api/v1/accounts/" + unknown)).body()).contains("\"code\":\"account-not-found\"");
        assertThat(send(get("/api/v1/journal-entries/" + unknown)).body()).contains("\"code\":\"entry-not-found\"");
        assertThat(send(get("/api/v1/accounts/no-es-uuid")).statusCode()).isEqualTo(400);
        HttpResponse<String> badLimit = send(get("/api/v1/accounts/" + savings + "/entries?limit=500"));
        assertThat(badLimit.statusCode()).isEqualTo(400);
        assertThat(badLimit.body()).contains("\"field\":\"limit\"");
        assertThat(send(get("/api/v1/accounts/" + savings + "/entries?cursor=basura")).body())
                .contains("\"code\":\"invalid-request\"");
        assertThat(send(get("/api/v1/no-existe")).body()).contains("\"code\":\"not-found\"");
        assertThat(balance(savings)).isEqualTo("100000.00");
    }

    @Test
    void theProvisionalPhase3RoutesAreGone() throws Exception {
        assertThat(send(post("/transfers", "{}").header("Idempotency-Key", newKey())).statusCode()).isEqualTo(404);
        assertThat(send(get("/accounts/" + savings + "/balance")).statusCode()).isEqualTo(404);
    }

    @Test
    void theApiRequiresTheTokenButTheDocumentationDoesNot() throws Exception {
        HttpRequest withoutToken = HttpRequest.newBuilder(uri("/api/v1/accounts")).GET().build();
        assertThat(client.send(withoutToken, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        HttpResponse<String> ui = client.send(HttpRequest.newBuilder(uri("/swagger-ui.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui");
        assertThat(ui.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                csp -> assertThat(csp).contains("style-src 'self' 'unsafe-inline'"));

        HttpResponse<String> docs = client.send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("\"/api/v1/transfers\"");
    }

    private String createAccount(String name, String type) throws Exception {
        HttpResponse<String> response = send(post("/api/v1/accounts",
                "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}").header("Idempotency-Key", newKey()));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return idOf(response.body());
    }

    private HttpResponse<String> transfer(String key, String from, String to, String amount) throws Exception {
        HttpRequest.Builder request = post("/api/v1/transfers", """
                {"from":"%s","to":"%s","amount":"%s","date":"%s","description":"Transferencia"}"""
                .formatted(from, to, amount, DATE));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return send(request);
    }

    private String balance(String account) throws Exception {
        HttpResponse<String> response = send(get("/api/v1/accounts/" + account));
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
