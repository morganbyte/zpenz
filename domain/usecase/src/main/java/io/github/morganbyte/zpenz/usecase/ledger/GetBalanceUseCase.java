package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/** Calcula el saldo de una cuenta desde sus asientos. */
public class GetBalanceUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    public GetBalanceUseCase(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
    }

    /** @throws AccountNotFoundException si la cuenta no existe */
    public Money balanceOf(AccountId id) {
        Account account = accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        return account.balance(entries.totalsFor(id));
    }
}
