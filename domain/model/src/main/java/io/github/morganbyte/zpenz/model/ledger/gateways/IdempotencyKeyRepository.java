package io.github.morganbyte.zpenz.model.ledger.gateways;

import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;

import java.util.Optional;

/** Puerto de las claves de idempotencia (ADR-0008). */
public interface IdempotencyKeyRepository {

    /**
     * Reclama la clave para el asiento dado, que se debe registrar en la misma transacción.
     * Si otra transacción tiene la clave reclamada y no ha terminado, espera a que termine.
     * Si la transacción se deshace, la clave queda libre otra vez.
     *
     * @return vacío si la clave quedó reclamada; si ya estaba registrada, el asiento que produjo
     */
    Optional<JournalEntryId> claim(IdempotencyKey key, JournalEntryId entryId);
}
