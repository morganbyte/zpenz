package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import io.github.morganbyte.zpenz.model.ledger.gateways.Transactions;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Ejecuta una operación una sola vez por clave, en una transacción (ADR-0008 y ADR-0009).
 * Primero reclama la clave; si ya existía, no registra nada y devuelve lo que la clave produjo,
 * siempre que la petición sea la misma.
 */
final class IdempotentRunner {

    private final IdempotencyKeyRepository keys;
    private final Transactions transactions;

    IdempotentRunner(IdempotencyKeyRepository keys, Transactions transactions) {
        this.keys = Objects.requireNonNull(keys);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * @param resourceId el identificador del recurso que {@code create} va a registrar
     * @param create     registra el recurso; corre solo si la clave quedó reclamada
     * @param previous   lee el recurso que produjo la clave
     * @param sameRequest si el recurso guardado corresponde a esta petición
     * @throws IdempotencyKeyReusedException si la clave se usó en otra operación o con otra petición
     */
    <T> Idempotent<T> run(IdempotencyKey key, IdempotentOperation operation, UUID resourceId,
                          Supplier<T> create, Function<UUID, T> previous, Predicate<T> sameRequest) {
        Objects.requireNonNull(key, "La clave de idempotencia es obligatoria");
        return transactions.inTransaction(() -> {
            Optional<IdempotencyKeyRepository.Claim> claimed = keys.claim(key, operation, resourceId);
            if (claimed.isEmpty()) {
                return new Idempotent<>(create.get(), false);
            }
            IdempotencyKeyRepository.Claim claim = claimed.get();
            if (claim.operation() != operation) {
                throw new IdempotencyKeyReusedException(key);
            }
            T stored = previous.apply(claim.resourceId());
            if (!sameRequest.test(stored)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return new Idempotent<>(stored, true);
        });
    }

    static IllegalStateException dangling(IdempotencyKey key, UUID resourceId) {
        return new IllegalStateException(
                "La clave " + key.value() + " apunta a " + resourceId + ", que no existe");
    }
}
