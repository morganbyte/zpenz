package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;

import java.time.LocalDate;
import java.util.Objects;

/** Anula un asiento con su reverso. El asiento original no cambia. */
public class ReverseJournalEntryUseCase {

    private final JournalEntryRepository entries;

    public ReverseJournalEntryUseCase(JournalEntryRepository entries) {
        this.entries = Objects.requireNonNull(entries);
    }

    /**
     * @throws JournalEntryNotFoundException si el asiento no existe
     * @throws EntryAlreadyReversedException si el asiento ya tiene un reverso
     * @throws IllegalStateException si el asiento ya es un reverso
     */
    public JournalEntry reverse(JournalEntryId id, LocalDate date, String description) {
        JournalEntry original = entries.findById(id).orElseThrow(() -> new JournalEntryNotFoundException(id));
        if (entries.isReversed(id)) {
            throw new EntryAlreadyReversedException(id);
        }
        JournalEntry reversal = original.reverse(JournalEntryId.random(), date, description);
        // Si otro reverso entra entre la consulta y este registro, la base lo rechaza (ADR-0007).
        entries.add(reversal);
        return reversal;
    }
}
