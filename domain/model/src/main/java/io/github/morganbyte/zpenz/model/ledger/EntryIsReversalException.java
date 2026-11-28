package io.github.morganbyte.zpenz.model.ledger;

/** Un reverso no se reversa: si el movimiento debía existir, se registra un asiento nuevo (ADR-0006). */
public class EntryIsReversalException extends IllegalStateException {

    private final transient JournalEntryId journalEntryId;

    public EntryIsReversalException(JournalEntryId journalEntryId) {
        super("El asiento " + journalEntryId.value() + " ya es un reverso y no se puede reversar; registre un asiento nuevo");
        this.journalEntryId = journalEntryId;
    }

    public JournalEntryId journalEntryId() {
        return journalEntryId;
    }
}
