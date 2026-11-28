package io.github.morganbyte.zpenz.model.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un monto en COP, exacto, con dos decimales (ADR-0004 y ADR-0006).
 *
 * <p>Siempre tiene escala 2, así que {@code equals} compara el valor: {@code 10} y
 * {@code 10.00} son el mismo monto. Rechaza más de dos decimales en lugar de
 * redondear y rechaza montos que no caben en {@code NUMERIC(19,2)}.
 */
public final class Money implements Comparable<Money> {

    public static final int SCALE = 2;

    /** El mayor valor absoluto que cabe en {@code NUMERIC(19,2)}: 17 dígitos enteros y 2 decimales. */
    public static final BigDecimal MAX_ABSOLUTE = new BigDecimal("99999999999999999.99");

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        Objects.requireNonNull(amount, "El monto es obligatorio");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException(
                    "El monto " + amount.toPlainString() + " tiene más de " + SCALE + " decimales");
        }
        if (amount.abs().compareTo(MAX_ABSOLUTE) > 0) {
            throw new IllegalArgumentException(
                    "El monto " + amount.toPlainString() + " no cabe en NUMERIC(19,2)");
        }
        this.amount = amount.setScale(SCALE);
    }

    /** Construye un monto desde texto, por ejemplo {@code "1500.50"}. */
    public static Money of(String amount) {
        Objects.requireNonNull(amount, "El monto es obligatorio");
        try {
            return new Money(new BigDecimal(amount.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El monto \"" + amount + "\" no es un número válido", e);
        }
    }

    /**
     * Construye un monto desde un {@code BigDecimal}. Un {@code BigDecimal} creado desde
     * un {@code double} con {@code new BigDecimal(0.1)} arrastra decimales de más y se rechaza.
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    /** Construye un monto de pesos enteros, sin centavos. */
    public static Money ofPesos(long pesos) {
        return new Money(BigDecimal.valueOf(pesos));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " COP";
    }
}
