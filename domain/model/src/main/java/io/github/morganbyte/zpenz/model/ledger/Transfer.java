package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Mover un monto de una cuenta a otra (ADR-0008). Se registra como un asiento de dos líneas:
 * crédito a la cuenta de origen y débito a la de destino.
 */
public record Transfer(AccountId from, AccountId to, Money amount, LocalDate date, String description) {

    public Transfer {
        Objects.requireNonNull(from, "La cuenta de origen es obligatoria");
        Objects.requireNonNull(to, "La cuenta de destino es obligatoria");
        Objects.requireNonNull(amount, "El monto es obligatorio");
        Objects.requireNonNull(date, "La fecha es obligatoria");
        Objects.requireNonNull(description, "La descripción es obligatoria");
        if (from.equals(to)) {
            throw new IllegalArgumentException("La cuenta de origen y la de destino deben ser distintas");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El monto de una transferencia debe ser mayor que cero: " + amount);
        }
        description = description.strip();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("La descripción de la transferencia no puede estar vacía");
        }
        if (description.length() > JournalEntry.DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("La descripción de la transferencia no puede pasar de "
                    + JournalEntry.DESCRIPTION_MAX_LENGTH + " caracteres");
        }
    }

    /** El asiento que registra esta transferencia. */
    public JournalEntry toEntry(JournalEntryId id) {
        return JournalEntry.post(id, date, description,
                List.of(EntryLine.debit(to, amount), EntryLine.credit(from, amount)));
    }

    /**
     * Si el asiento dado es el que registraría esta transferencia: mismas líneas, fecha y
     * descripción. Sirve para reconocer la repetición de una transferencia ya registrada.
     */
    public boolean matches(JournalEntry entry) {
        Objects.requireNonNull(entry, "El asiento es obligatorio");
        return !entry.isReversal()
                && entry.date().equals(date)
                && entry.description().equals(description)
                && entry.lines().equals(List.of(EntryLine.debit(to, amount), EntryLine.credit(from, amount)));
    }
}
