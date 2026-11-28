package io.github.morganbyte.zpenz.usecase.ledger;

import java.util.Objects;

/** Lo que produjo una operación con clave de idempotencia y si es la repetición de una ya registrada. */
public record Idempotent<T>(T value, boolean replayed) {

    public Idempotent {
        Objects.requireNonNull(value, "El resultado es obligatorio");
    }
}
