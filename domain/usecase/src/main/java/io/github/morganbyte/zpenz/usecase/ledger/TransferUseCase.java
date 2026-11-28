package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.InsufficientFundsException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.Transfer;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;
import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Registra una transferencia completa o nada, una sola vez por clave de idempotencia y sin
 * dejar en negativo una cuenta de activo aunque lleguen varias al mismo tiempo (ADR-0008).
 */
public class TransferUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdempotencyKeyRepository keys;
    private final Transactions transactions;

    public TransferUseCase(AccountRepository accounts, JournalEntryRepository entries,
                           IdempotencyKeyRepository keys, Transactions transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
        this.keys = Objects.requireNonNull(keys);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** El asiento de la transferencia y si es la repetición de una ya registrada. */
    public record Result(JournalEntry entry, boolean replayed) {
    }

    /**
     * @throws AccountNotFoundException si el origen o el destino no existen
     * @throws InsufficientFundsException si el origen es un activo y quedaría en negativo
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otra transferencia
     */
    public Result transfer(IdempotencyKey key, Transfer transfer) {
        Objects.requireNonNull(key, "La clave de idempotencia es obligatoria");
        Objects.requireNonNull(transfer, "La transferencia es obligatoria");
        return transactions.inTransaction(() -> {
            JournalEntryId entryId = JournalEntryId.random();
            // Primero la clave: una repetición simultánea espera aquí a que esta termine.
            Optional<JournalEntryId> previous = keys.claim(key, entryId);
            if (previous.isPresent()) {
                return replay(key, transfer, previous.get());
            }
            // Después el origen: otra transferencia desde la misma cuenta espera aquí.
            Account source = accounts.lockById(transfer.from())
                    .orElseThrow(() -> new AccountNotFoundException(transfer.from()));
            if (accounts.findById(transfer.to()).isEmpty()) {
                throw new AccountNotFoundException(transfer.to());
            }
            if (source.type() == AccountType.ASSET) {
                Money balance = source.balance(entries.totalsFor(source.id()));
                if (balance.minus(transfer.amount()).isNegative()) {
                    throw new InsufficientFundsException(source.id(), balance, transfer.amount());
                }
            }
            JournalEntry entry = transfer.toEntry(entryId);
            entries.add(entry);
            return new Result(entry, false);
        });
    }

    private Result replay(IdempotencyKey key, Transfer transfer, JournalEntryId previousId) {
        JournalEntry previous = entries.findById(previousId)
                .orElseThrow(() -> new IllegalStateException(
                        "La clave " + key.value() + " apunta al asiento " + previousId.value() + ", que no existe"));
        if (!transfer.matches(previous)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return new Result(previous, true);
    }
}
