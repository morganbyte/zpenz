package io.github.morganbyte.zpenz.model.ledger;

public class JournalEntryNotFoundException extends RuntimeException {

    private final transient JournalEntryId journalEntryId;

    public JournalEntryNotFoundException(JournalEntryId journalEntryId) {
        super("No existe el asiento " + journalEntryId.value());
        this.journalEntryId = journalEntryId;
    }

    public JournalEntryId journalEntryId() {
        return journalEntryId;
    }
}
