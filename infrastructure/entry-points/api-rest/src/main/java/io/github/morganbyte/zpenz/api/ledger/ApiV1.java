package io.github.morganbyte.zpenz.api.ledger;

/** Constantes de la API v1 (ADR-0009). */
final class ApiV1 {

    static final String BASE = "/api/v1";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    static final String PROBLEM_JSON = "application/problem+json";

    /** Monto positivo con hasta dos decimales, como texto (ADR-0004 y ADR-0009). */
    static final String AMOUNT_PATTERN = "^\\d{1,17}(\\.\\d{1,2})?$";
    static final String AMOUNT_MESSAGE = "debe ser un monto en texto con hasta dos decimales, por ejemplo \"70000.50\"";

    /** ASCII visible, sin espacios (ADR-0008). */
    static final String KEY_PATTERN = "^[!-~]{1,255}$";
    static final String KEY_MESSAGE = "debe tener de 1 a 255 caracteres ASCII visibles, sin espacios";
    static final String KEY_DESCRIPTION = "Clave que identifica la petición para reintentarla sin duplicarla. "
            + "Se recomienda un UUID. La misma petición con la misma clave devuelve el mismo recurso con "
            + "`Idempotent-Replayed: true`; otra petición con la misma clave responde 422.";

    private ApiV1() {
    }
}
