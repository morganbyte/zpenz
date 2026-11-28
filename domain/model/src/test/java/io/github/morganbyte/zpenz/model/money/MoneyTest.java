package io.github.morganbyte.zpenz.model.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void alwaysHasScaleTwo() {
        assertThat(Money.of("1500").toBigDecimal()).isEqualByComparingTo("1500").hasScaleOf(2);
        assertThat(Money.of("1500.5").toBigDecimal().toPlainString()).isEqualTo("1500.50");
        assertThat(Money.ofPesos(7).toBigDecimal().toPlainString()).isEqualTo("7.00");
    }

    @Test
    void equalAmountsWithDifferentScaleAreEqual() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00")).hasSameHashCodeAs(Money.of("10.0"));
    }

    @Test
    void addsWithoutFloatingPointErrors() {
        assertThat(Money.of("0.1").plus(Money.of("0.2"))).isEqualTo(Money.of("0.30"));
    }

    @Test
    void subtractsAndNegates() {
        assertThat(Money.of("100").minus(Money.of("150.25"))).isEqualTo(Money.of("-50.25"));
        assertThat(Money.of("-50.25").negate()).isEqualTo(Money.of("50.25"));
    }

    @Test
    void reportsSign() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("0.01").isPositive()).isTrue();
        assertThat(Money.of("-0.01").isNegative()).isTrue();
    }

    @Test
    void comparesByValue() {
        assertThat(Money.of("9.99")).isLessThan(Money.of("10"));
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(Money.of("10.500")).isEqualTo(Money.of("10.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.005", "0.001"})
    void rejectsMoreThanTwoDecimalsInsteadOfRounding(String amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of(amount))
                .withMessageContaining("más de 2 decimales");
    }

    @Test
    void rejectsBigDecimalBuiltFromDouble() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of(new BigDecimal(0.1)));
    }

    @Test
    void acceptsTheLargestAmountThatFitsInNumeric19And2() {
        assertThat(Money.of("99999999999999999.99").toBigDecimal()).isEqualByComparingTo(Money.MAX_ABSOLUTE);
        assertThat(Money.of("-99999999999999999.99").isNegative()).isTrue();
    }

    @Test
    void rejectsAmountsThatDoNotFitInNumeric19And2() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("100000000000000000"))
                .withMessageContaining("NUMERIC(19,2)");
        Money max = Money.of(Money.MAX_ABSOLUTE);
        assertThatIllegalArgumentException().isThrownBy(() -> max.plus(Money.of("0.01")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1,5"})
    void rejectsText(String amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of(amount))
                .withMessageContaining("no es un número válido");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Money.of((String) null));
        assertThatNullPointerException().isThrownBy(() -> Money.of((BigDecimal) null));
    }

    @Test
    void printsAmountInCop() {
        assertThat(Money.of("1500.5")).hasToString("1500.50 COP");
    }
}
