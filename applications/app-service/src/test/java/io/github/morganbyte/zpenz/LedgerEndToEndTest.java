package io.github.morganbyte.zpenz;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.money.Money;
import io.github.morganbyte.zpenz.usecase.ledger.CreateAccountUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.GetBalanceUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.PostJournalEntryUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.ReverseJournalEntryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los casos de uso tal como los registra Spring, contra PostgreSQL con las migraciones de
 * Flyway aplicadas al arrancar.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class LedgerEndToEndTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    @Autowired
    private CreateAccountUseCase createAccount;
    @Autowired
    private PostJournalEntryUseCase postEntry;
    @Autowired
    private ReverseJournalEntryUseCase reverseEntry;
    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void recordsAMonthAndCalculatesBalancesFromTheDatabase() {
        Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
        Account card = createAccount.create("Tarjeta de crédito", AccountType.LIABILITY);
        Account salary = createAccount.create("Salario", AccountType.INCOME);
        Account groceries = createAccount.create("Mercado", AccountType.EXPENSE);

        postEntry.post(DATE, "Salario", List.of(
                debit(savings.id(), Money.of("4500000")), credit(salary.id(), Money.of("4500000"))));
        postEntry.post(DATE, "Mercado con tarjeta", List.of(
                debit(groceries.id(), Money.of("120000")), credit(card.id(), Money.of("120000"))));
        JournalEntry duplicated = postEntry.post(DATE, "Mercado con débito", List.of(
                debit(groceries.id(), Money.of("250000.50")), credit(savings.id(), Money.of("250000.50"))));
        reverseEntry.reverse(duplicated.id(), DATE.plusDays(1), "Reverso: mercado duplicado");

        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.of("4500000"));
        assertThat(getBalance.balanceOf(card.id())).isEqualTo(Money.of("120000"));
        assertThat(getBalance.balanceOf(salary.id())).isEqualTo(Money.of("4500000"));
        assertThat(getBalance.balanceOf(groceries.id())).isEqualTo(Money.of("120000"));
        assertThatThrownBy(() -> reverseEntry.reverse(duplicated.id(), DATE.plusDays(1), "Otra vez"))
                .isInstanceOf(EntryAlreadyReversedException.class);
    }
}
