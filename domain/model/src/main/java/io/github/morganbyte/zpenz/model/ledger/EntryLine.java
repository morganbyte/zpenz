package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/** Una línea de un asiento: una cuenta, un lado y un monto mayor que cero. */
public record EntryLine(AccountId accountId, Side side, Money amount) {

    public EntryLine {
        Objects.requireNonNull(accountId, "La cuenta de la línea es obligatoria");
        Objects.requireNonNull(side, "El lado de la línea es obligatorio");
        Objects.requireNonNull(amount, "El monto de la línea es obligatorio");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El monto de una línea debe ser mayor que cero: " + amount);
        }
    }

    public static EntryLine debit(AccountId accountId, Money amount) {
        return new EntryLine(accountId, Side.DEBIT, amount);
    }

    public static EntryLine credit(AccountId accountId, Money amount) {
        return new EntryLine(accountId, Side.CREDIT, amount);
    }

    /** La misma línea con el lado invertido, para un reverso. */
    EntryLine reversed() {
        return new EntryLine(accountId, side.opposite(), amount);
    }
}
