package io.github.morganbyte.zpenz.model.ledger;

/** La clave ya se usó para una petición distinta (ADR-0008). */
public class IdempotencyKeyReusedException extends RuntimeException {

    private final transient IdempotencyKey key;

    public IdempotencyKeyReusedException(IdempotencyKey key) {
        super("La clave de idempotencia " + key.value() + " ya se usó para una petición distinta");
        this.key = key;
    }

    public IdempotencyKey key() {
        return key;
    }
}
