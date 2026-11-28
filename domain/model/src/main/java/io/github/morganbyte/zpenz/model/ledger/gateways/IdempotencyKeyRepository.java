package io.github.morganbyte.zpenz.model.ledger.gateways;

import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Puerto de las claves de idempotencia (ADR-0008 y ADR-0009). */
public interface IdempotencyKeyRepository {

    /** Lo que una clave ya registrada produjo: la operación y el identificador del recurso. */
    record Claim(IdempotentOperation operation, UUID resourceId) {
        public Claim {
            Objects.requireNonNull(operation, "La operación es obligatoria");
            Objects.requireNonNull(resourceId, "El recurso es obligatorio");
        }
    }

    /**
     * Reclama la clave para el recurso dado (una cuenta o un asiento, según la operación), que
     * se debe registrar en la misma transacción. Si otra transacción tiene la clave reclamada y
     * no ha terminado, espera a que termine. Si la transacción se deshace, la clave queda libre.
     *
     * @return vacío si la clave quedó reclamada; si ya estaba registrada, lo que produjo
     */
    Optional<Claim> claim(IdempotencyKey key, IdempotentOperation operation, UUID resourceId);
}
