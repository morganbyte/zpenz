package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
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

/**
 * Registra una transferencia completa o nada, una sola vez por clave de idempotencia y sin
 * dejar en negativo una cuenta de activo aunque lleguen varias al mismo tiempo (ADR-0008).
 */
public class TransferUseCase {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdempotentRunner idempotent;

    public TransferUseCase(AccountRepository accounts, JournalEntryRepository entries,
                           IdempotencyKeyRepository keys, Transactions transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.entries = Objects.requireNonNull(entries);
        this.idempotent = new IdempotentRunner(keys, transactions);
    }

    /**
     * @throws AccountNotFoundException si el origen o el destino no existen
     * @throws InsufficientFundsException si el origen es un activo y quedaría en negativo
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otra petición
     */
    public Idempotent<JournalEntry> transfer(IdempotencyKey key, Transfer transfer) {
        Objects.requireNonNull(transfer, "La transferencia es obligatoria");
        JournalEntryId entryId = JournalEntryId.random();
        // La clave se reclama primero: una repetición simultánea espera ahí a que esta termine.
        return idempotent.run(key, IdempotentOperation.TRANSFER, entryId.value(),
                () -> record(transfer, entryId),
                id -> entries.findById(new JournalEntryId(id)).orElseThrow(() -> IdempotentRunner.dangling(key, id)),
                transfer::matches);
    }

    private JournalEntry record(Transfer transfer, JournalEntryId entryId) {
        // Otra transferencia desde la misma cuenta espera aquí hasta que esta termine.
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
        return entry;
    }
}
