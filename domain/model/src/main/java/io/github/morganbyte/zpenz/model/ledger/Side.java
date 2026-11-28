package io.github.morganbyte.zpenz.model.ledger;

/** Lado de una línea de asiento. */
public enum Side {
    DEBIT,
    CREDIT;

    public Side opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
