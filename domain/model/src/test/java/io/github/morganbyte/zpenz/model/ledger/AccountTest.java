package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccountTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final Account savings = new Account(AccountId.random(), "Cuenta de ahorros", AccountType.ASSET);
    private final Account creditCard = new Account(AccountId.random(), "Tarjeta de crédito", AccountType.LIABILITY);
    private final Account salary = new Account(AccountId.random(), "Salario", AccountType.INCOME);
    private final Account groceries = new Account(AccountId.random(), "Mercado", AccountType.EXPENSE);
    private final Account openingBalance = new Account(AccountId.random(), "Saldo inicial", AccountType.EQUITY);

    private static JournalEntry entry(String description, Account debited, Account credited, String amount) {
        return JournalEntry.post(JournalEntryId.random(), DATE, description,
                List.of(debit(debited.id(), Money.of(amount)), credit(credited.id(), Money.of(amount))));
    }

    @ParameterizedTest
    @CsvSource({"ASSET, DEBIT", "EXPENSE, DEBIT", "LIABILITY, CREDIT", "EQUITY, CREDIT", "INCOME, CREDIT"})
    void eachTypeGrowsOnItsNormalSide(AccountType type, Side normalSide) {
        assertThat(type.normalSide()).isEqualTo(normalSide);
    }

    @Test
    void hasZeroBalanceWithoutEntries() {
        assertThat(savings.balance(List.of())).isEqualTo(Money.ZERO);
    }

    @Test
    void calculatesTheBalanceOfEveryAccountFromTheEntries() {
        List<JournalEntry> month = List.of(
                entry("Saldo inicial", savings, openingBalance, "1000000"),
                entry("Salario de noviembre", savings, salary, "4500000"),
                entry("Mercado con débito", groceries, savings, "250000.50"),
                entry("Mercado con tarjeta", groceries, creditCard, "120000"),
                entry("Pago de la tarjeta", creditCard, savings, "100000"));

        assertThat(savings.balance(month)).isEqualTo(Money.of("5149999.50"));
        assertThat(creditCard.balance(month)).isEqualTo(Money.of("20000"));
        assertThat(salary.balance(month)).isEqualTo(Money.of("4500000"));
        assertThat(groceries.balance(month)).isEqualTo(Money.of("370000.50"));
        assertThat(openingBalance.balance(month)).isEqualTo(Money.of("1000000"));
    }

    @Test
    void canHaveANegativeBalance() {
        List<JournalEntry> overdraft = List.of(entry("Sobregiro", groceries, savings, "50000"));

        assertThat(savings.balance(overdraft)).isEqualTo(Money.of("-50000"));
    }

    @Test
    void aReversalCancelsTheOriginalInTheBalance() {
        JournalEntry duplicated = entry("Mercado duplicado", groceries, savings, "250000");
        JournalEntry reversal = duplicated.reverse(JournalEntryId.random(), DATE, "Reverso: mercado duplicado");

        assertThat(savings.balance(List.of(duplicated, reversal))).isEqualTo(Money.ZERO);
        assertThat(groceries.balance(List.of(duplicated, reversal))).isEqualTo(Money.ZERO);
    }

    @Test
    void theNormalBalancesOfAllAccountsAddUpToZeroOnEachSide() {
        List<JournalEntry> month = List.of(
                entry("Saldo inicial", savings, openingBalance, "1000000"),
                entry("Mercado con tarjeta", groceries, creditCard, "120000"));

        Money debitSide = savings.balance(month).plus(groceries.balance(month));
        Money creditSide = openingBalance.balance(month).plus(creditCard.balance(month));

        assertThat(debitSide).isEqualTo(creditSide);
    }

    @Test
    void stripsTheNameAndRejectsBlankNames() {
        assertThat(new Account(AccountId.random(), "  Efectivo ", AccountType.ASSET).name()).isEqualTo("Efectivo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Account(AccountId.random(), " ", AccountType.ASSET));
    }
}
