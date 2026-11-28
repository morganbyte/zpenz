-- Idempotencia en todas las operaciones que crean algo (ADR-0009). La V2 no se edita: se
-- modifica la tabla. Las claves que ya existen son de transferencias.

ALTER TABLE idempotency_key ADD COLUMN operation TEXT NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE idempotency_key ALTER COLUMN operation DROP DEFAULT;
ALTER TABLE idempotency_key ADD CONSTRAINT idempotency_key_operation_valid
    CHECK (operation IN ('ACCOUNT', 'TRANSFER', 'JOURNAL_ENTRY', 'REVERSAL'));

-- Una clave apunta a una cuenta o a un asiento, según la operación. Como con el asiento, la
-- cuenta todavía no existe cuando se reclama la clave: la referencia se comprueba al final.
ALTER TABLE idempotency_key ALTER COLUMN journal_entry_id DROP NOT NULL;
ALTER TABLE idempotency_key ADD COLUMN account_id UUID UNIQUE
    REFERENCES account (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE idempotency_key ADD CONSTRAINT idempotency_key_one_resource CHECK (
    CASE WHEN operation = 'ACCOUNT'
         THEN account_id IS NOT NULL AND journal_entry_id IS NULL
         ELSE journal_entry_id IS NOT NULL AND account_id IS NULL
    END);

-- Los asientos de una cuenta, del más reciente al más antiguo, paginados por cursor.
CREATE INDEX journal_entry_newest_first_idx ON journal_entry (entry_date DESC, recorded_at DESC, id DESC);
