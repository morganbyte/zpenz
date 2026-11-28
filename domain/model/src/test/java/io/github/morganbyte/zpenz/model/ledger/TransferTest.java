package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TransferTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);
    private final AccountId savings = AccountId.random();
    private final AccountId cash = AccountId.random();

    private Transfer transfer(String amount, String description) {
        return new Transfer(savings, cash, Money.of(amount), DATE, description);
    }

    @Test
    void isAnEntryThatCreditsTheSourceAndDebitsTheDestination() {
        JournalEntry entry = transfer("70000", "  Retiro  ").toEntry(JournalEntryId.random());

        assertThat(entry.lines()).containsExactly(
                EntryLine.debit(cash, Money.of("70000")), EntryLine.credit(savings, Money.of("70000")));
        assertThat(entry.description()).isEqualTo("Retiro");
        assertThat(entry.date()).isEqualTo(DATE);
    }

    @Test
    void recognisesTheEntryItProduced() {
        JournalEntry entry = transfer("70000", "Retiro").toEntry(JournalEntryId.random());

        assertThat(transfer("70000.00", " Retiro").matches(entry)).isTrue();
        assertThat(transfer("70000.01", "Retiro").matches(entry)).isFalse();
        assertThat(transfer("70000", "Otro").matches(entry)).isFalse();
        assertThat(new Transfer(cash, savings, Money.of("70000"), DATE, "Retiro").matches(entry)).isFalse();
        assertThat(new Transfer(savings, cash, Money.of("70000"), DATE.plusDays(1), "Retiro").matches(entry)).isFalse();
    }

    @Test
    void rejectsInvalidTransfers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transfer(savings, savings, Money.of("1"), DATE, "Retiro"))
                .withMessageContaining("distintas");
        assertThatIllegalArgumentException().isThrownBy(() -> transfer("0", "Retiro"));
        assertThatIllegalArgumentException().isThrownBy(() -> transfer("-1", "Retiro"));
        assertThatIllegalArgumentException().isThrownBy(() -> transfer("1", "   "));
        assertThatIllegalArgumentException().isThrownBy(() -> transfer("1", "x".repeat(256)));
    }

    @Test
    void idempotencyKeysAreVisibleAsciiUpTo255Characters() {
        assertThat(new IdempotencyKey("3f1c2a4e-9b7d-4c1e-8a2f-0d5e6b7c8a9b").value()).hasSize(36);
        assertThat(new IdempotencyKey("k".repeat(255)).value()).hasSize(255);
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey("k".repeat(256)));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey("con espacio"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyKey("clave-ñ"));
    }
}
