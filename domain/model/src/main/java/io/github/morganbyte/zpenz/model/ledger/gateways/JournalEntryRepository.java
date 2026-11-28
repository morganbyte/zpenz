package io.github.morganbyte.zpenz.model.ledger.gateways;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.SideTotals;

import java.util.Optional;

/** Puerto de los asientos. Solo agrega: un asiento no se edita ni se borra (ADR-0006). */
public interface JournalEntryRepository {

    /**
     * Registra el asiento con todas sus líneas, completo o nada.
     *
     * @throws EntryAlreadyReversedException si es un reverso de un asiento que ya tenía uno
     */
    void add(JournalEntry entry);

    Optional<JournalEntry> findById(JournalEntryId id);

    /** Si ya existe un reverso del asiento dado. */
    boolean isReversed(JournalEntryId id);

    /** Suma de los débitos y de los créditos de la cuenta en todos los asientos. */
    SideTotals totalsFor(AccountId accountId);

    /**
     * Los asientos con alguna línea en la cuenta, del más reciente al más antiguo: por fecha y,
     * en la misma fecha, por orden de registro (ADR-0009).
     *
     * @param after el {@code nextCursor} de la página anterior, o {@code null} para la primera
     * @throws IllegalArgumentException si el cursor no es uno que este repositorio produjo
     */
    EntryPage entriesOf(AccountId accountId, String after, int limit);
}
