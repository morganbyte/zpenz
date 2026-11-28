package io.github.morganbyte.zpenz.model.ledger;

import io.github.morganbyte.zpenz.model.money.Money;

import java.util.Objects;

/**
 * Una cuenta del libro. No guarda saldo: el saldo se calcula desde los asientos (ADR-0006).
 */
public record Account(AccountId id, String name, AccountType type) {

    public Account {
        Objects.requireNonNull(id, "El identificador de la cuenta es obligatorio");
        Objects.requireNonNull(name, "El nombre de la cuenta es obligatorio");
        Objects.requireNonNull(type, "El tipo de la cuenta es obligatorio");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cuenta no puede estar vacío");
        }
    }

    /**
     * Saldo de la cuenta según los asientos dados: las líneas del lado normal suman y las
     * del lado contrario restan. Las líneas de otras cuentas se ignoran.
     */
    public Money balance(Iterable<JournalEntry> entries) {
        Objects.requireNonNull(entries, "Los asientos son obligatorios");
        Money balance = Money.ZERO;
        for (JournalEntry entry : entries) {
            for (EntryLine line : entry.lines()) {
                if (line.accountId().equals(id)) {
                    balance = line.side() == type.normalSide()
                            ? balance.plus(line.amount())
                            : balance.minus(line.amount());
                }
            }
        }
        return balance;
    }
}
