package io.github.morganbyte.zpenz.model.ledger;

import java.util.Objects;
import java.util.UUID;

public record JournalEntryId(UUID value) {

    public JournalEntryId {
        Objects.requireNonNull(value, "El identificador del asiento es obligatorio");
    }

    public static JournalEntryId random() {
        return new JournalEntryId(UUID.randomUUID());
    }
}
