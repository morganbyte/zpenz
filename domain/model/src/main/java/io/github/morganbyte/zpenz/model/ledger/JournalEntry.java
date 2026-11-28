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

    public static final int DESCRIPTION_MAX_LENGTH = 255;

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
        if (this.description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "La descripción del asiento no puede pasar de " + DESCRIPTION_MAX_LENGTH + " caracteres");
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
     * Reconstruye un asiento ya registrado, por ejemplo al leerlo de la base. Aplica las
     * mismas reglas que {@link #post}: un asiento descuadrado no se puede reconstruir.
     *
     * @param reversalOf el asiento que este reversa, o {@code null} si no es un reverso
     */
    public static JournalEntry restore(JournalEntryId id, LocalDate date, String description,
                                       List<EntryLine> lines, JournalEntryId reversalOf) {
        return new JournalEntry(id, date, description, lines, reversalOf);
    }

    /**
     * Crea el asiento que anula a este: las mismas líneas con los lados invertidos. Este
     * asiento no cambia. Que un asiento se reverse una sola vez depende de los asientos ya
     * registrados y se controla al registrarlo (ADR-0007), no aquí.
     */
    public JournalEntry reverse(JournalEntryId reversalId, LocalDate reversalDate, String reversalDescription) {
        if (isReversal()) {
            throw new EntryIsReversalException(id);
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

    /**
     * Si los dos asientos dicen lo mismo: fecha, descripción, líneas en el mismo orden y el
     * mismo asiento reversado. No compara el identificador. Sirve para reconocer la repetición
     * de una petición (ADR-0009).
     */
    public boolean sameContentAs(JournalEntry other) {
        Objects.requireNonNull(other, "El asiento es obligatorio");
        return date.equals(other.date)
                && description.equals(other.description)
                && lines.equals(other.lines)
                && Objects.equals(reversalOf, other.reversalOf);
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
