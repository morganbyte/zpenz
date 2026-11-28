package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/**
 * Una cuenta del libro. No guarda saldo: el saldo se calcula desde los asientos (ADR-0006).
 */
public record Account(AccountId id, String name, AccountType type) {

    public static final int NAME_MAX_LENGTH = 100;

    public Account {
        Objects.requireNonNull(id, "El identificador de la cuenta es obligatorio");
        Objects.requireNonNull(name, "El nombre de la cuenta es obligatorio");
        Objects.requireNonNull(type, "El tipo de la cuenta es obligatorio");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cuenta no puede estar vacío");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "El nombre de la cuenta no puede pasar de " + NAME_MAX_LENGTH + " caracteres");
        }
    }

    /**
     * Saldo de la cuenta según los asientos dados. Las líneas de otras cuentas se ignoran.
     */
    public Money balance(Iterable<JournalEntry> entries) {
        Objects.requireNonNull(entries, "Los asientos son obligatorios");
        Money debits = Money.ZERO;
        Money credits = Money.ZERO;
        for (JournalEntry entry : entries) {
            for (EntryLine line : entry.lines()) {
                if (line.accountId().equals(id)) {
                    if (line.side() == Side.DEBIT) {
                        debits = debits.plus(line.amount());
                    } else {
                        credits = credits.plus(line.amount());
                    }
                }
            }
        }
        return balance(new SideTotals(debits, credits));
    }

    /**
     * Saldo de la cuenta a partir de la suma de sus débitos y créditos: el lado normal suma
     * y el contrario resta. Es la única regla de signos del sistema (ADR-0006 y ADR-0007).
     */
    public Money balance(SideTotals totals) {
        Objects.requireNonNull(totals, "Los totales son obligatorios");
        return type.normalSide() == Side.DEBIT
                ? totals.debits().minus(totals.credits())
                : totals.credits().minus(totals.debits());
    }
}
