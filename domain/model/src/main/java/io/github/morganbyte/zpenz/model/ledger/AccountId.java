package io.github.morganbyte.zpenz.model.ledger;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "El identificador de la cuenta es obligatorio");
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }
}
