package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.Side;
import io.github.morganbyte.zpenz.model.ledger.SideTotals;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;
import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementación en memoria de los puertos, solo para probar los casos de uso en un hilo. Una
 * transacción que lanza una excepción deshace lo que agregó, como en la base.
 */
class InMemoryLedger implements AccountRepository, JournalEntryRepository, IdempotencyKeyRepository, Transactions {

    final Map<AccountId, Account> accounts = new LinkedHashMap<>();
    final Map<JournalEntryId, JournalEntry> entries = new LinkedHashMap<>();
    final Map<IdempotencyKey, JournalEntryId> keys = new LinkedHashMap<>();
    int transactionsStarted;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        transactionsStarted++;
        Map<AccountId, Account> accountsBefore = new LinkedHashMap<>(accounts);
        Map<JournalEntryId, JournalEntry> entriesBefore = new LinkedHashMap<>(entries);
        Map<IdempotencyKey, JournalEntryId> keysBefore = new LinkedHashMap<>(keys);
        try {
            return work.get();
        } catch (RuntimeException e) {
            accounts.clear();
            accounts.putAll(accountsBefore);
            entries.clear();
            entries.putAll(entriesBefore);
            keys.clear();
            keys.putAll(keysBefore);
            throw e;
        }
    }

    @Override
    public Optional<JournalEntryId> claim(IdempotencyKey key, JournalEntryId entryId) {
        return Optional.ofNullable(keys.putIfAbsent(key, entryId));
    }

    @Override
    public Optional<Account> lockById(AccountId id) {
        return findById(id);
    }

    @Override
    public void add(Account account) {
        if (accounts.putIfAbsent(account.id(), account) != null) {
            throw new IllegalStateException("Cuenta duplicada");
        }
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Set<AccountId> findMissing(Collection<AccountId> ids) {
        return ids.stream().filter(id -> !accounts.containsKey(id)).collect(Collectors.toSet());
    }

    @Override
    public void add(JournalEntry entry) {
        entry.reversalOf().filter(this::isReversed).ifPresent(original -> {
            throw new EntryAlreadyReversedException(original);
        });
        if (entries.putIfAbsent(entry.id(), entry) != null) {
            throw new IllegalStateException("Asiento duplicado");
        }
    }

    @Override
    public Optional<JournalEntry> findById(JournalEntryId id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public boolean isReversed(JournalEntryId id) {
        return entries.values().stream().anyMatch(e -> e.reversalOf().filter(id::equals).isPresent());
    }

    @Override
    public SideTotals totalsFor(AccountId accountId) {
        Money debits = Money.ZERO;
        Money credits = Money.ZERO;
        for (JournalEntry entry : entries.values()) {
            for (EntryLine line : entry.lines()) {
                if (line.accountId().equals(accountId)) {
                    if (line.side() == Side.DEBIT) {
                        debits = debits.plus(line.amount());
                    } else {
                        credits = credits.plus(line.amount());
                    }
                }
            }
        }
        return new SideTotals(debits, credits);
    }
}
