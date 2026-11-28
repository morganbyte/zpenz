package io.github.morganbyte.zpenz.model.ledger;

import java.util.Objects;

/**
 * La clave con la que un cliente identifica una petición para poder reintentarla sin
 * duplicarla (ADR-0008). La genera el cliente; se recomienda un UUID.
 */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 255;

    public IdempotencyKey {
        Objects.requireNonNull(value, "La clave de idempotencia es obligatoria");
        if (value.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia no puede estar vacía");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "La clave de idempotencia no puede pasar de " + MAX_LENGTH + " caracteres");
        }
        if (value.chars().anyMatch(c -> c < 0x21 || c > 0x7E)) {
            throw new IllegalArgumentException(
                    "La clave de idempotencia solo admite caracteres ASCII visibles, sin espacios");
        }
    }
}
