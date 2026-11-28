package io.github.morganbyte.zpenz.model.ledger.gateways;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Puerto de las cuentas del libro. */
public interface AccountRepository {

    /** Registra una cuenta nueva. */
    void add(Account account);

    Optional<Account> findById(AccountId id);

    /** Todas las cuentas, en el orden en que se crearon. */
    List<Account> findAll();

    /**
     * Lee la cuenta y la bloquea hasta el final de la transacción: otra transacción que pida
     * el mismo bloqueo espera (ADR-0008). Solo tiene sentido dentro de
     * {@link Transactions#inTransaction}.
     */
    Optional<Account> lockById(AccountId id);

    /** De los identificadores dados, los que no corresponden a ninguna cuenta. */
    Set<AccountId> findMissing(Collection<AccountId> ids);
}
