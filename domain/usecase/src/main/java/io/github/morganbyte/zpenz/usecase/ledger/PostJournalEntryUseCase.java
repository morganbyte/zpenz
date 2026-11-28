package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Registra un asiento entre cuentas existentes. */
public class PostJournalEntryUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdempotentRunner idempotent;

    public PostJournalEntryUseCase(AccountRepository accounts, JournalEntryRepository entries,
                                   IdempotencyKeyRepository keys, Transactions transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
        this.idempotent = new IdempotentRunner(keys, transactions);
    }

    /**
     * @throws AccountNotFoundException si alguna línea apunta a una cuenta que no existe
     * @throws IllegalArgumentException si el asiento no cuadra o le falta un dato
     */
    public JournalEntry post(LocalDate date, String description, List<EntryLine> lines) {
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), date, description, lines);
        record(entry);
        return entry;
    }

    /**
     * Registra el asiento una sola vez por clave (ADR-0009). Repetir la petición devuelve el
     * mismo asiento.
     *
     * @throws AccountNotFoundException si alguna línea apunta a una cuenta que no existe
     * @throws IllegalArgumentException si el asiento no cuadra o le falta un dato
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otra petición
     */
    public Idempotent<JournalEntry> post(IdempotencyKey key, LocalDate date, String description, List<EntryLine> lines) {
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), date, description, lines);
        return idempotent.run(key, IdempotentOperation.JOURNAL_ENTRY, entry.id().value(),
                () -> {
                    record(entry);
                    return entry;
                },
                id -> entries.findById(new JournalEntryId(id)).orElseThrow(() -> IdempotentRunner.dangling(key, id)),
                entry::sameContentAs);
    }

    private void record(JournalEntry entry) {
        Set<AccountId> referenced = entry.lines().stream().map(EntryLine::accountId).collect(Collectors.toSet());
        accounts.findMissing(referenced).stream().findFirst().ifPresent(missing -> {
            throw new AccountNotFoundException(missing);
        });
        entries.add(entry);
    }
}
