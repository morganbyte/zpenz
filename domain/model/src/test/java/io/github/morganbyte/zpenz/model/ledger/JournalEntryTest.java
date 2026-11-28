package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);
    private final AccountId savings = AccountId.random();
    private final AccountId groceries = AccountId.random();
    private final AccountId creditCard = AccountId.random();

    private JournalEntry groceriesPaidWithSavings() {
        return JournalEntry.post(JournalEntryId.random(), DATE, "Mercado",
                List.of(debit(groceries, Money.of("250000")), credit(savings, Money.of("250000"))));
    }

    @Test
    void postsABalancedEntry() {
        JournalEntry entry = groceriesPaidWithSavings();

        assertThat(entry.total(Side.DEBIT)).isEqualTo(Money.of("250000"));
        assertThat(entry.total(Side.CREDIT)).isEqualTo(Money.of("250000"));
        assertThat(entry.isReversal()).isFalse();
        assertThat(entry.reversalOf()).isEmpty();
    }

    @Test
    void postsAnEntryWithSeveralLinesOnOneSide() {
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), DATE, "Mercado dividido",
                List.of(debit(groceries, Money.of("300000")),
                        credit(savings, Money.of("100000.50")),
                        credit(creditCard, Money.of("199999.50"))));

        assertThat(entry.lines()).hasSize(3);
    }

    @Test
    void rejectsAnUnbalancedEntry() {
        List<EntryLine> lines = List.of(debit(groceries, Money.of("250000")), credit(savings, Money.of("249999.99")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> JournalEntry.post(JournalEntryId.random(), DATE, "Mercado", lines))
                .withMessageContaining("no cuadra");
    }

    @Test
    void rejectsAnEntryWithoutBothSides() {
        List<EntryLine> onlyDebits = List.of(debit(groceries, Money.of("1")), debit(savings, Money.of("1")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> JournalEntry.post(JournalEntryId.random(), DATE, "Solo débitos", onlyDebits))
                .withMessageContaining("débito y una de crédito");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JournalEntry.post(JournalEntryId.random(), DATE, "Vacío", List.of()));
    }

    @Test
    void rejectsLinesWithZeroOrNegativeAmounts() {
        assertThatIllegalArgumentException().isThrownBy(() -> debit(groceries, Money.ZERO))
                .withMessageContaining("mayor que cero");
        assertThatIllegalArgumentException().isThrownBy(() -> credit(savings, Money.of("-5")));
    }

    @Test
    void rejectsABlankDescription() {
        List<EntryLine> lines = List.of(debit(groceries, Money.of("1")), credit(savings, Money.of("1")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> JournalEntry.post(JournalEntryId.random(), DATE, "   ", lines));
    }

    @Test
    void cannotBeChangedAfterPosting() {
        List<EntryLine> lines = new ArrayList<>(List.of(debit(groceries, Money.of("1")), credit(savings, Money.of("1"))));
        JournalEntry entry = JournalEntry.post(JournalEntryId.random(), DATE, "Mercado", lines);

        lines.add(debit(groceries, Money.of("1000")));

        assertThat(entry.lines()).hasSize(2);
        assertThatThrownBy(() -> entry.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reversesWithOppositeSidesAndKeepsTheOriginal() {
        JournalEntry original = groceriesPaidWithSavings();
        JournalEntryId reversalId = JournalEntryId.random();

        JournalEntry reversal = original.reverse(reversalId, DATE.plusDays(1), "Reverso: mercado duplicado");

        assertThat(reversal.id()).isEqualTo(reversalId);
        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.reversalOf()).contains(original.id());
        assertThat(reversal.lines()).containsExactly(
                credit(groceries, Money.of("250000")), debit(savings, Money.of("250000")));
        assertThat(original.lines()).containsExactly(
                debit(groceries, Money.of("250000")), credit(savings, Money.of("250000")));
        assertThat(original.isReversal()).isFalse();
    }

    @Test
    void canBeReversedOnTheSameDay() {
        assertThat(groceriesPaidWithSavings().reverse(JournalEntryId.random(), DATE, "Reverso").date()).isEqualTo(DATE);
    }

    @Test
    void rejectsAReversalDatedBeforeTheOriginal() {
        JournalEntry original = groceriesPaidWithSavings();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> original.reverse(JournalEntryId.random(), DATE.minusDays(1), "Reverso"))
                .withMessageContaining("fecha anterior");
    }

    @Test
    void aReversalCannotBeReversed() {
        JournalEntry reversal = groceriesPaidWithSavings().reverse(JournalEntryId.random(), DATE, "Reverso");

        assertThatIllegalStateException()
                .isThrownBy(() -> reversal.reverse(JournalEntryId.random(), DATE, "Reverso del reverso"))
                .withMessageContaining("ya es un reverso");
    }

    @Test
    void isIdentifiedByItsId() {
        JournalEntry entry = groceriesPaidWithSavings();
        JournalEntry sameId = JournalEntry.post(entry.id(), DATE.plusDays(3), "Otro",
                List.of(debit(savings, Money.of("1")), credit(groceries, Money.of("1"))));

        assertThat(entry).isEqualTo(sameId).hasSameHashCodeAs(sameId);
    }
}
