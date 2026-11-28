package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Transacciones de la base con el aislamiento por defecto de PostgreSQL, {@code READ COMMITTED}
 * (ADR-0008). Una excepción deshace la transacción. Los repositorios que ya abren su propia
 * transacción, como {@link PostgresJournalEntryRepository#add}, se unen a esta.
 */
@Component
public class SpringTransactions implements Transactions {

    private final TransactionTemplate transaction;

    public SpringTransactions(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
