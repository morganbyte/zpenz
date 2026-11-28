package io.github.morganbyte.zpenz.model.ledger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una página de asientos y, si hay más, el cursor para pedir la siguiente (ADR-0009). El
 * cursor es opaco: solo lo entiende el repositorio que lo produjo.
 */
public record EntryPage(List<JournalEntry> entries, Optional<String> nextCursor) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public EntryPage {
        entries = List.copyOf(Objects.requireNonNull(entries, "Los asientos son obligatorios"));
        Objects.requireNonNull(nextCursor, "El cursor es obligatorio (vacío si no hay más)");
    }

    /** @throws IllegalArgumentException si el límite está fuera de 1 a {@value #MAX_LIMIT} */
    public static int checkLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y " + MAX_LIMIT + ": " + limit);
        }
        return limit;
    }
}
