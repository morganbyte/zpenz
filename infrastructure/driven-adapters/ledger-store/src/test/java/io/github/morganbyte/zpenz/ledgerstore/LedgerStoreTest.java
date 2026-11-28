package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.SideTotals;
import io.github.morganbyte.zpenz.model.money.Money;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el adaptador y la migración contra PostgreSQL real, en la versión mayor de Neon
 * (ADR-0007). Cada prueba usa identificadores nuevos, así que no hace falta limpiar: las
 * tablas del libro, además, no admiten borrados.
 */
@Testcontainers
class LedgerStoreTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static JdbcClient jdbc;
    private static TransactionTemplate transaction;
    private static PostgresAccountRepository accounts;
    private static PostgresJournalEntryRepository entries;
    private static PostgresIdempotencyKeyRepository keys;
    private static SpringTransactions transactions;

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        accounts = new PostgresAccountRepository(jdbc);
        entries = new PostgresJournalEntryRepository(jdbc, transaction);
        keys = new PostgresIdempotencyKeyRepository(jdbc);
        transactions = new SpringTransactions(transaction);
    }

    private static Account newAccount(String name, AccountType type) {
        Account account = new Account(AccountId.random(), name, type);
        accounts.add(account);
        return account;
    }

    private static JournalEntry newEntry(Account debited, Account credited, String amount) {
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), DATE, "Mercado",
                List.of(debit(debited.id(), Money.of(amount)), credit(credited.id(), Money.of(amount))));
        entries.add(entry);
        return entry;
    }

    @Nested
    class Accounts {

        @Test
        void storesAndReadsAnAccount() {
            Account account = newAccount("Cuenta de ahorros", AccountType.ASSET);

            assertThat(accounts.findById(account.id())).contains(account);
            assertThat(accounts.findById(AccountId.random())).isEmpty();
        }

        @Test
        void reportsMissingAccounts() {
            Account existing = newAccount("Efectivo", AccountType.ASSET);
            AccountId missing = AccountId.random();

            assertThat(accounts.findMissing(List.of(existing.id(), missing, existing.id()))).containsExactly(missing);
            assertThat(accounts.findMissing(List.of())).isEmpty();
        }
    }

    @Nested
    class JournalEntries {

        @Test
        void storesAndReadsAnEntryWithItsLinesInOrder() {
            Account savings = newAccount("Ahorros", AccountType.ASSET);
            Account groceries = newAccount("Mercado", AccountType.EXPENSE);
            Account card = newAccount("Tarjeta", AccountType.LIABILITY);
            JournalEntry entry = JournalEntry.post(JournalEntryId.random(), DATE, "Mercado dividido",
                    List.of(debit(groceries.id(), Money.of("300000")),
                            credit(savings.id(), Money.of("100000.50")),
                            credit(card.id(), Money.of("199999.50"))));

            entries.add(entry);
            JournalEntry read = entries.findById(entry.id()).orElseThrow();

            assertThat(read.date()).isEqualTo(DATE);
            assertThat(read.description()).isEqualTo("Mercado dividido");
            assertThat(read.lines()).isEqualTo(entry.lines());
            assertThat(read.reversalOf()).isEmpty();
            assertThat(entries.findById(JournalEntryId.random())).isEmpty();
        }

        @Test
        void sumsDebitsAndCreditsOfAnAccount() {
            Account savings = newAccount("Ahorros", AccountType.ASSET);
            Account salary = newAccount("Salario", AccountType.INCOME);
            Account groceries = newAccount("Mercado", AccountType.EXPENSE);
            newEntry(savings, salary, "4500000");
            newEntry(groceries, savings, "250000.50");
            newEntry(groceries, savings, "0.01");

            SideTotals totals = entries.totalsFor(savings.id());

            assertThat(totals).isEqualTo(new SideTotals(Money.of("4500000"), Money.of("250000.51")));
            assertThat(savings.balance(totals)).isEqualTo(Money.of("4249999.49"));
            assertThat(entries.totalsFor(AccountId.random())).isEqualTo(SideTotals.NONE);
        }

        @Test
        void storesAReversalAndReversesOnlyOnce() {
            Account savings = newAccount("Ahorros", AccountType.ASSET);
            Account groceries = newAccount("Mercado", AccountType.EXPENSE);
            JournalEntry original = newEntry(groceries, savings, "1000");
            JournalEntry reversal = original.reverse(JournalEntryId.random(), DATE, "Reverso");

            entries.add(reversal);

            assertThat(entries.isReversed(original.id())).isTrue();
            assertThat(entries.isReversed(reversal.id())).isFalse();
            assertThat(entries.findById(reversal.id()).orElseThrow().reversalOf()).contains(original.id());
            assertThat(savings.balance(entries.totalsFor(savings.id()))).isEqualTo(Money.ZERO);

            JournalEntry secondReversal = original.reverse(JournalEntryId.random(), DATE, "Otro reverso");
            assertThatThrownBy(() -> entries.add(secondReversal))
                    .isInstanceOf(EntryAlreadyReversedException.class);
            assertThat(entries.findById(secondReversal.id())).isEmpty();
        }

        @Test
        void storesNothingWhenALineFails() {
            Account savings = newAccount("Ahorros", AccountType.ASSET);
            AccountId unknown = AccountId.random();
            JournalEntry entry = JournalEntry.post(JournalEntryId.random(), DATE, "Cuenta inexistente",
                    List.of(debit(savings.id(), Money.of("1")), credit(unknown, Money.of("1"))));

            assertThatThrownBy(() -> entries.add(entry)).hasMessageContaining("entry_line_account_id_fkey");

            assertThat(entries.findById(entry.id())).isEmpty();
            assertThat(entries.totalsFor(savings.id())).isEqualTo(SideTotals.NONE);
        }
    }

    /** Escrituras que no pasan por el dominio: la base misma las rechaza (ADR-0007). */
    @Nested
    class DatabaseRules {

        private UUID account(AccountType type) {
            return newAccount("Cuenta", type).id().value();
        }

        private void insertEntry(UUID id, LocalDate date, UUID reversalOf) {
            jdbc.sql("INSERT INTO journal_entry (id, entry_date, description, reversal_of) VALUES (?, ?, 'SQL', ?)")
                    .params(id, date, reversalOf).update();
        }

        private void insertLine(UUID entry, int number, UUID account, String side, String amount) {
            jdbc.sql("INSERT INTO entry_line (journal_entry_id, line_number, account_id, side, amount) VALUES (?, ?, ?, ?, ?::numeric)")
                    .params(entry, number, account, side, amount).update();
        }

        private void inTransaction(Runnable sql) {
            transaction.executeWithoutResult(status -> sql.run());
        }

        @Test
        void rejectsAnUnbalancedEntryAtCommit() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);
            UUID entry = UUID.randomUUID();

            assertThatThrownBy(() -> inTransaction(() -> {
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "100");
                insertLine(entry, 2, credited, "CREDIT", "99.99");
            })).hasStackTraceContaining("no cuadra");

            assertThat(entries.findById(new JournalEntryId(entry))).isEmpty();
        }

        @Test
        void rejectsAnEntryWithoutLinesOrWithOneSide() {
            UUID debited = account(AccountType.EXPENSE);

            assertThatThrownBy(() -> inTransaction(() -> insertEntry(UUID.randomUUID(), DATE, null)))
                    .hasStackTraceContaining("no cuadra");
            assertThatThrownBy(() -> inTransaction(() -> {
                UUID entry = UUID.randomUUID();
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "100");
            })).hasStackTraceContaining("no cuadra");
        }

        @Test
        void acceptsABalancedEntryWrittenWithSql() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);
            UUID entry = UUID.randomUUID();

            inTransaction(() -> {
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "60");
                insertLine(entry, 2, debited, "DEBIT", "40");
                insertLine(entry, 3, credited, "CREDIT", "100");
            });

            assertThat(entries.findById(new JournalEntryId(entry)).orElseThrow().lines()).hasSize(3);
        }

        @Test
        void rejectsInvalidValues() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);

            assertThatThrownBy(() -> inTransaction(() -> {
                UUID entry = UUID.randomUUID();
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "-5");
                insertLine(entry, 2, credited, "CREDIT", "-5");
            })).hasStackTraceContaining("entry_line_amount_positive");
            assertThatThrownBy(() -> inTransaction(() -> {
                UUID entry = UUID.randomUUID();
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "IZQUIERDA", "5");
            })).hasStackTraceContaining("entry_line_side_valid");
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO account (id, name, type) VALUES (?, ' ', 'ASSET')")
                    .params(UUID.randomUUID()).update())
                    .hasStackTraceContaining("account_name_not_blank");
            assertThatThrownBy(() -> jdbc.sql("INSERT INTO account (id, name, type) VALUES (?, 'Cuenta', 'OTRO')")
                    .params(UUID.randomUUID()).update())
                    .hasStackTraceContaining("account_type_valid");
        }

        /**
         * Límite conocido (ADR-0007): PostgreSQL redondea a dos decimales al guardar en
         * NUMERIC(19,2), antes de los CHECK y los triggers, así que la base no puede rechazar
         * un monto con más decimales. Solo el dominio lo rechaza.
         */
        @Test
        void roundsAmountsWithMoreThanTwoDecimalsWrittenWithSql() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);
            UUID entry = UUID.randomUUID();

            inTransaction(() -> {
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "10.005");
                insertLine(entry, 2, credited, "CREDIT", "10.01");
            });

            assertThat(jdbc.sql("SELECT amount::text FROM entry_line WHERE journal_entry_id = ? ORDER BY line_number")
                    .params(entry).query(String.class).list()).containsExactly("10.01", "10.01");
            assertThatThrownBy(() -> inTransaction(() -> {
                UUID tiny = UUID.randomUUID();
                insertEntry(tiny, DATE, null);
                insertLine(tiny, 1, debited, "DEBIT", "0.001");
            })).hasStackTraceContaining("entry_line_amount_positive");
        }

        @Test
        void storesAmountsWithTwoDecimals() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);
            UUID entry = UUID.randomUUID();

            inTransaction(() -> {
                insertEntry(entry, DATE, null);
                insertLine(entry, 1, debited, "DEBIT", "10.5");
                insertLine(entry, 2, credited, "CREDIT", "10.50");
            });

            assertThat(jdbc.sql("SELECT amount::text FROM entry_line WHERE journal_entry_id = ? ORDER BY line_number")
                    .params(entry).query(String.class).list()).containsExactly("10.50", "10.50");
        }

        @Test
        void entriesAndLinesCannotBeChangedOrDeleted() {
            JournalEntry entry = newEntry(newAccount("Mercado", AccountType.EXPENSE), newAccount("Ahorros", AccountType.ASSET), "100");
            UUID id = entry.id().value();

            assertThatThrownBy(() -> jdbc.sql("UPDATE journal_entry SET description = 'Otro' WHERE id = ?").params(id).update())
                    .hasStackTraceContaining("solo admite inserciones");
            assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_entry WHERE id = ?").params(id).update())
                    .hasStackTraceContaining("solo admite inserciones");
            assertThatThrownBy(() -> jdbc.sql("UPDATE entry_line SET amount = 1 WHERE journal_entry_id = ?").params(id).update())
                    .hasStackTraceContaining("solo admite inserciones");
            assertThatThrownBy(() -> jdbc.sql("DELETE FROM entry_line WHERE journal_entry_id = ?").params(id).update())
                    .hasStackTraceContaining("solo admite inserciones");
            assertThatThrownBy(() -> jdbc.sql("TRUNCATE entry_line, journal_entry, idempotency_key").update())
                    .hasStackTraceContaining("solo admite inserciones");

            assertThat(entries.findById(entry.id()).orElseThrow().description()).isEqualTo("Mercado");
        }

        @Test
        void aReversalCannotBeReversedOrDatedBeforeTheOriginal() {
            UUID debited = account(AccountType.EXPENSE);
            UUID credited = account(AccountType.ASSET);
            UUID original = UUID.randomUUID();
            UUID reversal = UUID.randomUUID();
            inTransaction(() -> {
                insertEntry(original, DATE, null);
                insertLine(original, 1, debited, "DEBIT", "5");
                insertLine(original, 2, credited, "CREDIT", "5");
            });

            assertThatThrownBy(() -> inTransaction(() -> {
                UUID early = UUID.randomUUID();
                insertEntry(early, DATE.minusDays(1), original);
                insertLine(early, 1, debited, "CREDIT", "5");
                insertLine(early, 2, credited, "DEBIT", "5");
            })).hasStackTraceContaining("fecha anterior");

            inTransaction(() -> {
                insertEntry(reversal, DATE, original);
                insertLine(reversal, 1, debited, "CREDIT", "5");
                insertLine(reversal, 2, credited, "DEBIT", "5");
            });
            assertThatThrownBy(() -> inTransaction(() -> {
                UUID again = UUID.randomUUID();
                insertEntry(again, DATE, reversal);
                insertLine(again, 1, debited, "DEBIT", "5");
                insertLine(again, 2, credited, "CREDIT", "5");
            })).hasStackTraceContaining("ya es un reverso");
        }
    }

    /** Lo que necesitan las transferencias (ADR-0008): bloquear el origen y reclamar la clave. */
    @Nested
    class TransferSupport {

        private static final long WAIT_MS = 500;

        private JournalEntry unsavedEntry(Account debited, Account credited, String amount) {
            return JournalEntry.post(JournalEntryId.random(), DATE, "Transferencia",
                    List.of(debit(debited.id(), Money.of(amount)), credit(credited.id(), Money.of(amount))));
        }

        /** Abre una transacción en otro hilo que bloquea la cuenta hasta que se suelte el latch. */
        private CompletableFuture<Void> holdLock(Account account, CountDownLatch locked, CountDownLatch release) {
            return CompletableFuture.runAsync(() -> transactions.inTransaction(() -> {
                assertThat(accounts.lockById(account.id())).contains(account);
                locked.countDown();
                await(release);
                return null;
            }));
        }

        private static void await(CountDownLatch latch) {
            try {
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Test
        void lockingAnAccountMakesTheNextLockWait() throws Exception {
            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> first = holdLock(savings, locked, release);
            await(locked);

            CompletableFuture<Optional<Account>> second = CompletableFuture.supplyAsync(
                    () -> transactions.inTransaction(() -> accounts.lockById(savings.id())));
            Thread.sleep(WAIT_MS);
            assertThat(second).isNotDone();

            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).contains(savings);
        }

        @Test
        void aLockedAccountStillAcceptsEntries() throws Exception {
            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            Account salary = newAccount("Salario", AccountType.INCOME);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> lock = holdLock(savings, locked, release);
            await(locked);

            // La clave foránea de la línea toma FOR KEY SHARE, que no choca con FOR NO KEY UPDATE.
            CompletableFuture<Void> deposit = CompletableFuture.runAsync(
                    () -> entries.add(unsavedEntry(savings, salary, "1000")));
            try {
                deposit.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                lock.get(10, TimeUnit.SECONDS);
            }
            assertThat(entries.totalsFor(savings.id()).debits()).isEqualTo(Money.of("1000"));
        }

        @Test
        void lockingAnUnknownAccountFindsNothing() {
            assertThat(transactions.inTransaction(() -> accounts.lockById(AccountId.random()))).isEmpty();
        }

        @Test
        void claimsAKeyOnceAndReturnsTheEntryOnTheNextClaim() {
            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            Account cash = newAccount("Efectivo", AccountType.ASSET);
            IdempotencyKey key = new IdempotencyKey(UUID.randomUUID().toString());
            JournalEntry entry = unsavedEntry(cash, savings, "500");

            Optional<JournalEntryId> first = transactions.inTransaction(() -> {
                Optional<JournalEntryId> claim = keys.claim(key, entry.id());
                entries.add(entry);
                return claim;
            });
            Optional<JournalEntryId> second = transactions.inTransaction(
                    () -> keys.claim(key, JournalEntryId.random()));

            assertThat(first).isEmpty();
            assertThat(second).contains(entry.id());
        }

        @Test
        void aKeyClaimedWithoutItsEntryCannotBeCommitted() {
            IdempotencyKey key = new IdempotencyKey(UUID.randomUUID().toString());

            assertThatThrownBy(() -> transactions.inTransaction(() -> keys.claim(key, JournalEntryId.random())))
                    .hasStackTraceContaining("idempotency_key_journal_entry_id_fkey");
            assertThat(jdbc.sql("SELECT count(*) FROM idempotency_key WHERE key = :key")
                    .param("key", key.value()).query(Long.class).single()).isZero();
        }

        @Test
        void aRolledBackClaimFreesTheKey() {
            IdempotencyKey key = new IdempotencyKey(UUID.randomUUID().toString());

            assertThatThrownBy(() -> transactions.inTransaction(() -> {
                keys.claim(key, JournalEntryId.random());
                throw new IllegalStateException("sin fondos");
            })).hasMessage("sin fondos");

            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            Account cash = newAccount("Efectivo", AccountType.ASSET);
            JournalEntry entry = unsavedEntry(cash, savings, "1");
            assertThat(transactions.inTransaction(() -> {
                Optional<JournalEntryId> claim = keys.claim(key, entry.id());
                entries.add(entry);
                return claim;
            })).isEmpty();
        }

        @Test
        void aConcurrentClaimWaitsForTheFirstToCommit() throws Exception {
            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            Account cash = newAccount("Efectivo", AccountType.ASSET);
            IdempotencyKey key = new IdempotencyKey(UUID.randomUUID().toString());
            JournalEntry entry = unsavedEntry(cash, savings, "500");
            CountDownLatch claimed = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transactions.inTransaction(() -> {
                assertThat(keys.claim(key, entry.id())).isEmpty();
                claimed.countDown();
                await(release);
                entries.add(entry);
                return null;
            }));
            await(claimed);
            CompletableFuture<Optional<JournalEntryId>> second = CompletableFuture.supplyAsync(
                    () -> transactions.inTransaction(() -> keys.claim(key, JournalEntryId.random())));
            Thread.sleep(WAIT_MS);
            assertThat(second).isNotDone();

            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).contains(entry.id());
        }

        @Test
        void rejectsInvalidKeysWrittenWithSql() {
            Account savings = newAccount("Cuenta de ahorros", AccountType.ASSET);
            Account cash = newAccount("Efectivo", AccountType.ASSET);
            JournalEntry entry = newEntry(cash, savings, "1");

            for (String invalid : List.of("", "con espacio", "k".repeat(256))) {
                assertThatThrownBy(() -> jdbc.sql("INSERT INTO idempotency_key (key, journal_entry_id) VALUES (:k, :e)")
                        .param("k", invalid).param("e", entry.id().value()).update())
                        .as(invalid).hasStackTraceContaining("idempotency_key_");
            }
        }
    }
}
