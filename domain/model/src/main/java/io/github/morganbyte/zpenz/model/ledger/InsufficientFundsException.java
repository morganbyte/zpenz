package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

/** Una transferencia no puede dejar en negativo una cuenta de activo (ADR-0008). */
public class InsufficientFundsException extends RuntimeException {

    private final transient AccountId accountId;
    private final transient Money balance;
    private final transient Money amount;

    public InsufficientFundsException(AccountId accountId, Money balance, Money amount) {
        super("La cuenta " + accountId.value() + " tiene " + balance + " y no alcanza para transferir " + amount);
        this.accountId = accountId;
        this.balance = balance;
        this.amount = amount;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money balance() {
        return balance;
    }

    public Money amount() {
        return amount;
    }
}
