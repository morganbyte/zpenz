package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountBalance;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;

import java.util.List;
import java.util.Objects;

/** Consultas de cuentas: con su saldo y con sus asientos (ADR-0009). */
public class QueryAccountsUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    public QueryAccountsUseCase(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
    }

    /** Todas las cuentas con su saldo, en el orden en que se crearon. */
    public List<AccountBalance> list() {
        return accounts.findAll().stream().map(this::withBalance).toList();
    }

    /** @throws AccountNotFoundException si la cuenta no existe */
    public AccountBalance get(AccountId id) {
        return withBalance(find(id));
    }

    /**
     * Los asientos de la cuenta, del más reciente al más antiguo.
     *
     * @param after el cursor de la página anterior, o {@code null} para la primera
     * @throws AccountNotFoundException si la cuenta no existe
     * @throws IllegalArgumentException si el límite o el cursor no son válidos
     */
    public EntryPage entries(AccountId id, String after, int limit) {
        EntryPage.checkLimit(limit);
        find(id);
        return entries.entriesOf(id, after, limit);
    }

    private Account find(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private AccountBalance withBalance(Account account) {
        return new AccountBalance(account, account.balance(entries.totalsFor(account.id())));
    }
}
