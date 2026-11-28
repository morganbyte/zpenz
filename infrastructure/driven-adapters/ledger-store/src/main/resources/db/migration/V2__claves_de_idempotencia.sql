-- Claves de idempotencia de las transferencias (ADR-0008).
-- La clave se reclama al empezar la transacción, antes de que exista el asiento: por eso la
-- referencia al asiento se comprueba al final de la transacción. Una clave reclamada sin
-- asiento no se puede confirmar.

CREATE TABLE idempotency_key (
    key               TEXT        PRIMARY KEY,
    journal_entry_id  UUID        NOT NULL UNIQUE
                                  REFERENCES journal_entry (id) DEFERRABLE INITIALLY DEFERRED,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_key_length CHECK (char_length(key) BETWEEN 1 AND 255),
    CONSTRAINT idempotency_key_visible_ascii CHECK (key ~ '^[!-~]+$')
);
