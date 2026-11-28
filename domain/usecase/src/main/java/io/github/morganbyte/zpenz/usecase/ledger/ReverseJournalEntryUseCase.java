package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryIsReversalException;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;

import java.time.LocalDate;
import java.util.Objects;

/** Anula un asiento con su reverso. El asiento original no cambia. */
public class ReverseJournalEntryUseCase {

    private final JournalEntryRepository entries;
    private final IdempotentRunner idempotent;

    public ReverseJournalEntryUseCase(JournalEntryRepository entries, IdempotencyKeyRepository keys,
                                      Transactions transactions) {
        this.entries = Objects.requireNonNull(entries);
        this.idempotent = new IdempotentRunner(keys, transactions);
    }

    /**
     * @throws JournalEntryNotFoundException si el asiento no existe
     * @throws EntryAlreadyReversedException si el asiento ya tiene un reverso
     * @throws EntryIsReversalException si el asiento ya es un reverso
     */
    public JournalEntry reverse(JournalEntryId id, LocalDate date, String description) {
        return record(id, JournalEntryId.random(), date, description);
    }

    /**
     * Reversa el asiento una sola vez por clave (ADR-0009). Repetir la petición devuelve el
     * mismo reverso; reversar con otra clave un asiento ya reversado sigue fallando.
     *
     * @throws JournalEntryNotFoundException si el asiento no existe
     * @throws EntryAlreadyReversedException si el asiento ya tiene un reverso de otra petición
     * @throws EntryIsReversalException si el asiento ya es un reverso
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otra petición
     */
    public Idempotent<JournalEntry> reverse(IdempotencyKey key, JournalEntryId id, LocalDate date, String description) {
        Objects.requireNonNull(id, "El asiento a reversar es obligatorio");
        JournalEntryId reversalId = JournalEntryId.random();
        return idempotent.run(key, IdempotentOperation.REVERSAL, reversalId.value(),
                () -> record(id, reversalId, date, description),
                stored -> entries.findById(new JournalEntryId(stored))
                        .orElseThrow(() -> IdempotentRunner.dangling(key, stored)),
                stored -> stored.reversalOf().filter(id::equals).isPresent()
                        && stored.date().equals(date)
                        && description != null && stored.description().equals(description.strip()));
    }

    private JournalEntry record(JournalEntryId id, JournalEntryId reversalId, LocalDate date, String description) {
        JournalEntry original = entries.findById(id).orElseThrow(() -> new JournalEntryNotFoundException(id));
        if (entries.isReversed(id)) {
            throw new EntryAlreadyReversedException(id);
        }
        JournalEntry reversal = original.reverse(reversalId, date, description);
        // Si otro reverso entra entre la consulta y este registro, la base lo rechaza (ADR-0007).
        entries.add(reversal);
        return reversal;
    }
}
