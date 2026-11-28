package io.github.morganbyte.zpenz.model.ledger;

/**
 * Las operaciones que aceptan {@code Idempotency-Key} (ADR-0009). Una clave usada en una
 * operación no sirve para otra.
 */
public enum IdempotentOperation {
    /** Crear una cuenta: la clave apunta a la cuenta. */
    ACCOUNT,
    /** Registrar una transferencia: la clave apunta a su asiento. */
    TRANSFER,
    /** Registrar un asiento general: la clave apunta al asiento. */
    JOURNAL_ENTRY,
    /** Reversar un asiento: la clave apunta al reverso. */
    REVERSAL;

    /** Si el recurso que produce esta operación es una cuenta; si no, es un asiento. */
    public boolean producesAccount() {
        return this == ACCOUNT;
    }
}
