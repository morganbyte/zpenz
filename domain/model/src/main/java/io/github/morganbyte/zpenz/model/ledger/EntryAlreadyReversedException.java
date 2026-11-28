package io.github.morganbyte.zpenz.model.ledger;

/** Un asiento se reversa una sola vez (ADR-0007). */
public class EntryAlreadyReversedException extends RuntimeException {

    private final transient JournalEntryId journalEntryId;

    public EntryAlreadyReversedException(JournalEntryId journalEntryId) {
        super("El asiento " + journalEntryId.value() + " ya fue reversado");
        this.journalEntryId = journalEntryId;
    }

    public JournalEntryId journalEntryId() {
        return journalEntryId;
    }
}
