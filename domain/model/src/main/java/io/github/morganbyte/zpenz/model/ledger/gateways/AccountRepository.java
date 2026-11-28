package io.github.morganbyte.zpenz.model.ledger.gateways;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Puerto de las cuentas del libro. */
public interface AccountRepository {

    /** Registra una cuenta nueva. */
    void add(Account account);

    Optional<Account> findById(AccountId id);

    /** De los identificadores dados, los que no corresponden a ninguna cuenta. */
    Set<AccountId> findMissing(Collection<AccountId> ids);
}
