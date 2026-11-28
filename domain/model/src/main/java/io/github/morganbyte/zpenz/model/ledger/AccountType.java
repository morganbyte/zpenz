package io.github.morganbyte.zpenz.model.ledger;

/** Tipos de cuenta y el lado por el que crece cada uno (ADR-0006). */
public enum AccountType {
    ASSET(Side.DEBIT),
    EXPENSE(Side.DEBIT),
    LIABILITY(Side.CREDIT),
    EQUITY(Side.CREDIT),
    INCOME(Side.CREDIT);

    private final Side normalSide;

    AccountType(Side normalSide) {
        this.normalSide = normalSide;
    }

    public Side normalSide() {
        return normalSide;
    }
}
