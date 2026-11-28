package io.github.morganbyte.zpenz.model.ledger.gateways;

import java.util.function.Supplier;

/**
 * Puerto para que un caso de uso marque una transacción sin conocer Spring (ADR-0008). Todo
 * lo que corre dentro se confirma junto o, si lanza una excepción, se deshace junto.
 */
public interface Transactions {

    <T> T inTransaction(Supplier<T> work);
}
