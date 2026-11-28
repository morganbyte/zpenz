package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountBalance;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryIsReversalException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las operaciones con {@code Idempotency-Key} y las consultas de la Fase 4 (ADR-0009). */
class IdempotentUseCasesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final CreateAccountUseCase createAccount = new CreateAccountUseCase(ledger, ledger, ledger);
    private final PostJournalEntryUseCase postEntry = new PostJournalEntryUseCase(ledger, ledger, ledger, ledger);
    private final ReverseJournalEntryUseCase reverseEntry = new ReverseJournalEntryUseCase(ledger, ledger, ledger);
    private final QueryAccountsUseCase queries = new QueryAccountsUseCase(ledger, ledger);
    private final GetJournalEntryUseCase getEntry = new GetJournalEntryUseCase(ledger);

    private static IdempotencyKey newKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    private List<EntryLine> groceries(Account expense, Account savings, String amount) {
        return List.of(debit(expense.id(), Money.of(amount)), credit(savings.id(), Money.of(amount)));
    }

    @Nested
    class Accounts {

        @Test
        void aRepeatedRequestReturnsTheSameAccount() {
            IdempotencyKey key = newKey();

            Idempotent<Account> first = createAccount.create(key, "Cuenta de ahorros", AccountType.ASSET);
            Idempotent<Account> second = createAccount.create(key, "  Cuenta de ahorros ", AccountType.ASSET);

            assertThat(first.replayed()).isFalse();
            assertThat(second.replayed()).isTrue();
            assertThat(second.value()).isEqualTo(first.value());
            assertThat(ledger.accounts).hasSize(1);
        }

        @Test
        void rejectsReusingTheKeyForAnotherAccount() {
            IdempotencyKey key = newKey();
            createAccount.create(key, "Cuenta de ahorros", AccountType.ASSET);

            assertThatThrownBy(() -> createAccount.create(key, "Cuenta de ahorros", AccountType.LIABILITY))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
            assertThatThrownBy(() -> createAccount.create(key, "Otra", AccountType.ASSET))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void aKeyUsedInOneOperationDoesNotServeAnother() {
            IdempotencyKey key = newKey();
            Account savings = createAccount.create(key, "Cuenta de ahorros", AccountType.ASSET).value();
            Account expense = createAccount.create("Mercado", AccountType.EXPENSE);

            assertThatThrownBy(() -> postEntry.post(key, DATE, "Mercado", groceries(expense, savings, "1")))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
            assertThat(ledger.entries).isEmpty();
        }

        @Test
        void listsAndReadsAccountsWithTheirBalance() {
            Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
            Account expense = createAccount.create("Mercado", AccountType.EXPENSE);
            postEntry.post(DATE, "Mercado", groceries(expense, savings, "1500"));

            assertThat(queries.list()).containsExactly(
                    new AccountBalance(savings, Money.of("-1500")), new AccountBalance(expense, Money.of("1500")));
            assertThat(queries.get(expense.id()).balance()).isEqualTo(Money.of("1500"));
            assertThatThrownBy(() -> queries.get(AccountId.random())).isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    class Entries {

        private final Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
        private final Account expense = createAccount.create("Mercado", AccountType.EXPENSE);

        @Test
        void aRepeatedRequestReturnsTheSameEntry() {
            IdempotencyKey key = newKey();

            Idempotent<JournalEntry> first = postEntry.post(key, DATE, "Mercado", groceries(expense, savings, "1500"));
            Idempotent<JournalEntry> second = postEntry.post(key, DATE, "Mercado", groceries(expense, savings, "1500.00"));

            assertThat(second.replayed()).isTrue();
            assertThat(second.value()).isEqualTo(first.value());
            assertThat(ledger.entries).hasSize(1);
            assertThatThrownBy(() -> postEntry.post(key, DATE, "Mercado", groceries(expense, savings, "1501")))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void aFailedEntryFreesTheKey() {
            IdempotencyKey key = newKey();
            Account ghost = new Account(AccountId.random(), "Fantasma", AccountType.ASSET);

            assertThatThrownBy(() -> postEntry.post(key, DATE, "Mercado", groceries(expense, ghost, "1")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(ledger.keys).isEmpty();
            assertThat(postEntry.post(key, DATE, "Mercado", groceries(expense, savings, "1")).replayed()).isFalse();
        }

        @Test
        void readsAnEntry() {
            JournalEntry entry = postEntry.post(DATE, "Mercado", groceries(expense, savings, "1500"));

            assertThat(getEntry.get(entry.id())).isEqualTo(entry);
            assertThatThrownBy(() -> getEntry.get(JournalEntryId.random()))
                    .isInstanceOf(JournalEntryNotFoundException.class);
        }

        @Test
        void pagesTheEntriesOfAnAccountFromNewestToOldest() {
            JournalEntry oldest = postEntry.post(DATE.minusDays(2), "Uno", groceries(expense, savings, "1"));
            JournalEntry middle = postEntry.post(DATE.minusDays(1), "Dos", groceries(expense, savings, "2"));
            JournalEntry newest = postEntry.post(DATE, "Tres", groceries(expense, savings, "3"));

            EntryPage first = queries.entries(savings.id(), null, 2);
            EntryPage second = queries.entries(savings.id(), first.nextCursor().orElseThrow(), 2);

            assertThat(first.entries()).containsExactly(newest, middle);
            assertThat(second.entries()).containsExactly(oldest);
            assertThat(second.nextCursor()).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> queries.entries(savings.id(), null, 0));
            assertThatIllegalArgumentException().isThrownBy(() -> queries.entries(savings.id(), null, 201));
            assertThatThrownBy(() -> queries.entries(AccountId.random(), null, 10))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    class Reversals {

        private final Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
        private final Account expense = createAccount.create("Mercado", AccountType.EXPENSE);
        private final JournalEntry entry = postEntry.post(DATE, "Mercado", groceries(expense, savings, "1500"));

        @Test
        void aRepeatedReversalReturnsTheSameReversal() {
            IdempotencyKey key = newKey();

            Idempotent<JournalEntry> first = reverseEntry.reverse(key, entry.id(), DATE, "Reverso");
            Idempotent<JournalEntry> second = reverseEntry.reverse(key, entry.id(), DATE, " Reverso ");

            assertThat(first.replayed()).isFalse();
            assertThat(second.replayed()).isTrue();
            assertThat(second.value()).isEqualTo(first.value());
            assertThat(first.value().reversalOf()).contains(entry.id());
            assertThat(ledger.entries).hasSize(2);
        }

        @Test
        void anotherKeyCannotReverseTheSameEntryAgain() {
            reverseEntry.reverse(newKey(), entry.id(), DATE, "Reverso");

            assertThatThrownBy(() -> reverseEntry.reverse(newKey(), entry.id(), DATE, "Reverso"))
                    .isInstanceOf(EntryAlreadyReversedException.class);
        }

        @Test
        void rejectsReusingTheKeyForAnotherReversal() {
            IdempotencyKey key = newKey();
            reverseEntry.reverse(key, entry.id(), DATE, "Reverso");

            assertThatThrownBy(() -> reverseEntry.reverse(key, entry.id(), DATE.plusDays(1), "Reverso"))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void aReversalCannotBeReversed() {
            JournalEntry reversal = reverseEntry.reverse(newKey(), entry.id(), DATE, "Reverso").value();

            assertThatThrownBy(() -> reverseEntry.reverse(newKey(), reversal.id(), DATE, "Otra vez"))
                    .isInstanceOf(EntryIsReversalException.class);
        }
    }
}
