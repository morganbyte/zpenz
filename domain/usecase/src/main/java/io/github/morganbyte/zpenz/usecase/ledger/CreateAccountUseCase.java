package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;

import java.util.Objects;

/** Abre una cuenta nueva. Toda cuenta empieza con saldo cero: no hay saldo que guardar. */
public class CreateAccountUseCase {

    private final AccountRepository accounts;
    private final IdempotentRunner idempotent;

    public CreateAccountUseCase(AccountRepository accounts, IdempotencyKeyRepository keys, Transactions transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.idempotent = new IdempotentRunner(keys, transactions);
    }

    public Account create(String name, AccountType type) {
        Account account = new Account(AccountId.random(), name, type);
        accounts.add(account);
        return account;
    }

    /**
     * Crea la cuenta una sola vez por clave (ADR-0009). Repetir la petición devuelve la misma cuenta.
     *
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otra petición
     */
    public Idempotent<Account> create(IdempotencyKey key, String name, AccountType type) {
        Account account = new Account(AccountId.random(), name, type);
        return idempotent.run(key, IdempotentOperation.ACCOUNT, account.id().value(),
                () -> {
                    accounts.add(account);
                    return account;
                },
                id -> accounts.findById(new AccountId(id)).orElseThrow(() -> IdempotentRunner.dangling(key, id)),
                stored -> stored.name().equals(account.name()) && stored.type() == account.type());
    }
}
