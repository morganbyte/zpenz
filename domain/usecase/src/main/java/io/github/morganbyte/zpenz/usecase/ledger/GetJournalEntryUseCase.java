package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;

import java.util.Objects;

/** Lee un asiento con sus líneas. */
public class GetJournalEntryUseCase {

    private final JournalEntryRepository entries;

    public GetJournalEntryUseCase(JournalEntryRepository entries) {
        this.entries = Objects.requireNonNull(entries);
    }

    /** @throws JournalEntryNotFoundException si el asiento no existe */
    public JournalEntry get(JournalEntryId id) {
        return entries.findById(id).orElseThrow(() -> new JournalEntryNotFoundException(id));
    }
}
