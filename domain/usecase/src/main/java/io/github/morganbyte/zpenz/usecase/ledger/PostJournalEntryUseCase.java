package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Registra un asiento entre cuentas existentes. */
public class PostJournalEntryUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    public PostJournalEntryUseCase(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
    }

    /**
     * @throws AccountNotFoundException si alguna línea apunta a una cuenta que no existe
     * @throws IllegalArgumentException si el asiento no cuadra o le falta un dato
     */
    public JournalEntry post(LocalDate date, String description, List<EntryLine> lines) {
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), date, description, lines);
        Set<AccountId> referenced = entry.lines().stream().map(EntryLine::accountId).collect(Collectors.toSet());
        accounts.findMissing(referenced).stream().findFirst().ifPresent(missing -> {
            throw new AccountNotFoundException(missing);
        });
        entries.add(entry);
        return entry;
    }
}
