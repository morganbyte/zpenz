package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;

import java.util.Objects;

/** Abre una cuenta nueva. Toda cuenta empieza con saldo cero: no hay saldo que guardar. */
public class CreateAccountUseCase {

    private final AccountRepository accounts;

    public CreateAccountUseCase(AccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    public Account create(String name, AccountType type) {
        Account account = new Account(AccountId.random(), name, type);
        accounts.add(account);
        return account;
    }
}
