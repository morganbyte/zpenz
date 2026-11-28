package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryNotFoundException;
import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerUseCasesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final CreateAccountUseCase createAccount = new CreateAccountUseCase(ledger);
    private final PostJournalEntryUseCase postEntry = new PostJournalEntryUseCase(ledger, ledger);
    private final ReverseJournalEntryUseCase reverseEntry = new ReverseJournalEntryUseCase(ledger);
    private final GetBalanceUseCase getBalance = new GetBalanceUseCase(ledger, ledger);

    private final Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
    private final Account salary = createAccount.create("Salario", AccountType.INCOME);
    private final Account groceries = createAccount.create("Mercado", AccountType.EXPENSE);

    private JournalEntry payGroceries(String amount) {
        return postEntry.post(DATE, "Mercado",
                List.of(debit(groceries.id(), Money.of(amount)), credit(savings.id(), Money.of(amount))));
    }

    @Test
    void createsAccountsWithZeroBalance() {
        assertThat(ledger.accounts).containsKeys(savings.id(), salary.id(), groceries.id());
        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.ZERO);
    }

    @Test
    void postsEntriesAndCalculatesBalances() {
        postEntry.post(DATE, "Salario",
                List.of(debit(savings.id(), Money.of("4500000")), credit(salary.id(), Money.of("4500000"))));
        payGroceries("250000.50");

        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.of("4249999.50"));
        assertThat(getBalance.balanceOf(salary.id())).isEqualTo(Money.of("4500000"));
        assertThat(getBalance.balanceOf(groceries.id())).isEqualTo(Money.of("250000.50"));
    }

    @Test
    void rejectsAnEntryAgainstAnAccountThatDoesNotExist() {
        AccountId unknown = AccountId.random();

        assertThatThrownBy(() -> postEntry.post(DATE, "Mercado",
                List.of(debit(unknown, Money.of("1")), credit(savings.id(), Money.of("1")))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(unknown.value().toString());
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    void doesNotRecordAnUnbalancedEntry() {
        assertThatIllegalArgumentException().isThrownBy(() -> postEntry.post(DATE, "Mercado",
                List.of(debit(groceries.id(), Money.of("2")), credit(savings.id(), Money.of("1")))));
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    void reversesAnEntryAndTheBalanceReturnsToZero() {
        JournalEntry duplicated = payGroceries("250000");

        JournalEntry reversal = reverseEntry.reverse(duplicated.id(), DATE, "Reverso: mercado duplicado");

        assertThat(reversal.reversalOf()).contains(duplicated.id());
        assertThat(ledger.entries).containsKeys(duplicated.id(), reversal.id());
        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.ZERO);
        assertThat(getBalance.balanceOf(groceries.id())).isEqualTo(Money.ZERO);
    }

    @Test
    void anEntryIsReversedOnlyOnce() {
        JournalEntry entry = payGroceries("1000");
        reverseEntry.reverse(entry.id(), DATE, "Reverso");

        assertThatThrownBy(() -> reverseEntry.reverse(entry.id(), DATE, "Otro reverso"))
                .isInstanceOf(EntryAlreadyReversedException.class);
        assertThat(ledger.entries).hasSize(2);
    }

    @Test
    void aReversalCannotBeReversed() {
        JournalEntry reversal = reverseEntry.reverse(payGroceries("1000").id(), DATE, "Reverso");

        assertThatIllegalStateException().isThrownBy(() -> reverseEntry.reverse(reversal.id(), DATE, "Reverso del reverso"));
    }

    @Test
    void reportsEntriesAndAccountsThatDoNotExist() {
        JournalEntryId unknownEntry = JournalEntryId.random();
        AccountId unknownAccount = AccountId.random();

        assertThatThrownBy(() -> reverseEntry.reverse(unknownEntry, DATE, "Reverso"))
                .isInstanceOf(JournalEntryNotFoundException.class);
        assertThatThrownBy(() -> getBalance.balanceOf(unknownAccount))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
