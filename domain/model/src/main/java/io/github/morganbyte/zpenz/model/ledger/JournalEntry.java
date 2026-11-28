package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un asiento de doble partida. Es inmutable y no puede existir descuadrado: débitos y
 * créditos suman lo mismo (ADR-0006). Un error se corrige con {@link #reverse}.
 */
public final class JournalEntry {

    private final JournalEntryId id;
    private final LocalDate date;
    private final String description;
    private final List<EntryLine> lines;
    private final JournalEntryId reversalOf;

    private JournalEntry(JournalEntryId id, LocalDate date, String description,
                         List<EntryLine> lines, JournalEntryId reversalOf) {
        this.id = Objects.requireNonNull(id, "El identificador del asiento es obligatorio");
        this.date = Objects.requireNonNull(date, "La fecha del asiento es obligatoria");
        Objects.requireNonNull(description, "La descripción del asiento es obligatoria");
        this.description = description.strip();
        if (this.description.isEmpty()) {
            throw new IllegalArgumentException("La descripción del asiento no puede estar vacía");
        }
        Objects.requireNonNull(lines, "Las líneas del asiento son obligatorias");
        this.lines = List.copyOf(lines);
        this.reversalOf = reversalOf;
        checkBalanced();
    }

    /** Registra un asiento nuevo. Falla si no tiene débito y crédito o si no cuadra. */
    public static JournalEntry post(JournalEntryId id, LocalDate date, String description, List<EntryLine> lines) {
        return new JournalEntry(id, date, description, lines, null);
    }

    /**
     * Crea el asiento que anula a este: las mismas líneas con los lados invertidos. Este
     * asiento no cambia. Que un asiento se reverse una sola vez se controla donde se
     * registran los asientos (Fase 3), no aquí.
     */
    public JournalEntry reverse(JournalEntryId reversalId, LocalDate reversalDate, String reversalDescription) {
        if (isReversal()) {
            throw new IllegalStateException(
                    "El asiento " + id.value() + " ya es un reverso y no se puede reversar; registre un asiento nuevo");
        }
        Objects.requireNonNull(reversalDate, "La fecha del reverso es obligatoria");
        if (reversalDate.isBefore(date)) {
            throw new IllegalArgumentException(
                    "El reverso (" + reversalDate + ") no puede tener fecha anterior al asiento original (" + date + ")");
        }
        List<EntryLine> reversedLines = lines.stream().map(EntryLine::reversed).toList();
        return new JournalEntry(reversalId, reversalDate, reversalDescription, reversedLines, id);
    }

    private void checkBalanced() {
        Money debits = total(Side.DEBIT);
        Money credits = total(Side.CREDIT);
        if (debits.isZero() || credits.isZero()) {
            throw new IllegalArgumentException("Un asiento necesita al menos una línea de débito y una de crédito");
        }
        if (!debits.equals(credits)) {
            throw new IllegalArgumentException(
                    "El asiento no cuadra: débitos " + debits + " y créditos " + credits);
        }
    }

    /** Suma de las líneas de un lado. */
    public Money total(Side side) {
        Money total = Money.ZERO;
        for (EntryLine line : lines) {
            if (line.side() == side) {
                total = total.plus(line.amount());
            }
        }
        return total;
    }

    public boolean isReversal() {
        return reversalOf != null;
    }

    public JournalEntryId id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public String description() {
        return description;
    }

    public List<EntryLine> lines() {
        return lines;
    }

    /** El asiento que este reversa, si es un reverso. */
    public Optional<JournalEntryId> reversalOf() {
        return Optional.ofNullable(reversalOf);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JournalEntry other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "JournalEntry[" + id.value() + ", " + date + ", " + description + ", " + lines.size() + " líneas]";
    }
}
