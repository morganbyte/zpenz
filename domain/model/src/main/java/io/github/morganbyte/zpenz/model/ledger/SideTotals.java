package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/** Suma de los débitos y de los créditos de una cuenta. */
public record SideTotals(Money debits, Money credits) {

    public static final SideTotals NONE = new SideTotals(Money.ZERO, Money.ZERO);

    public SideTotals {
        Objects.requireNonNull(debits, "El total de débitos es obligatorio");
        Objects.requireNonNull(credits, "El total de créditos es obligatorio");
        if (debits.isNegative() || credits.isNegative()) {
            throw new IllegalArgumentException("Los totales por lado no pueden ser negativos");
        }
    }
}
