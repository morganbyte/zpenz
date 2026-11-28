package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/** Una cuenta con su saldo calculado en el momento de la consulta. */
public record AccountBalance(Account account, Money balance) {

    public AccountBalance {
        Objects.requireNonNull(account, "La cuenta es obligatoria");
        Objects.requireNonNull(balance, "El saldo es obligatorio");
    }
}
