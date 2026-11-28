-- Libro contable de doble partida (ADR-0006 y ADR-0007).
-- Las reglas del dominio también se aplican aquí, para que ninguna escritura que no
-- pase por el dominio deje el libro inconsistente.

CREATE TABLE account (
    id          UUID        PRIMARY KEY,
    name        TEXT        NOT NULL,
    type        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT account_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT account_name_max_length CHECK (char_length(name) <= 100),
    CONSTRAINT account_type_valid CHECK (type IN ('ASSET', 'EXPENSE', 'LIABILITY', 'EQUITY', 'INCOME'))
);

CREATE TABLE journal_entry (
    id           UUID        PRIMARY KEY,
    entry_date   DATE        NOT NULL,
    description  TEXT        NOT NULL,
    reversal_of  UUID        REFERENCES journal_entry (id),
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT journal_entry_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT journal_entry_description_max_length CHECK (char_length(description) <= 255),
    -- Un asiento se reversa una sola vez.
    CONSTRAINT journal_entry_reversed_once UNIQUE (reversal_of)
);

CREATE TABLE entry_line (
    journal_entry_id  UUID          NOT NULL REFERENCES journal_entry (id),
    line_number       SMALLINT      NOT NULL,
    account_id        UUID          NOT NULL REFERENCES account (id),
    side              TEXT          NOT NULL,
    amount            NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (journal_entry_id, line_number),
    CONSTRAINT entry_line_number_positive CHECK (line_number > 0),
    CONSTRAINT entry_line_side_valid CHECK (side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT entry_line_amount_positive CHECK (amount > 0)
);

-- El saldo de una cuenta suma sus líneas.
CREATE INDEX entry_line_account_idx ON entry_line (account_id);

-- Débitos = créditos, con al menos una línea de cada lado. Se comprueba al final de la
-- transacción (DEFERRABLE INITIALLY DEFERRED), cuando el asiento ya tiene todas sus líneas.
CREATE FUNCTION check_journal_entry_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    entry_id UUID;
    debits   NUMERIC(19,2);
    credits  NUMERIC(19,2);
BEGIN
    IF TG_TABLE_NAME = 'journal_entry' THEN
        entry_id := NEW.id;
    ELSE
        entry_id := NEW.journal_entry_id;
    END IF;

    SELECT coalesce(sum(amount) FILTER (WHERE side = 'DEBIT'), 0),
           coalesce(sum(amount) FILTER (WHERE side = 'CREDIT'), 0)
      INTO debits, credits
      FROM entry_line
     WHERE journal_entry_id = entry_id;

    IF debits = 0 OR credits = 0 OR debits <> credits THEN
        RAISE EXCEPTION 'El asiento % no cuadra: débitos %, créditos %', entry_id, debits, credits
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER journal_entry_balanced
    AFTER INSERT ON journal_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_entry_balanced();

CREATE CONSTRAINT TRIGGER entry_line_balanced
    AFTER INSERT ON entry_line
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_entry_balanced();

-- Un reverso no se reversa y no puede tener fecha anterior al asiento original.
CREATE FUNCTION check_journal_entry_reversal() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    original journal_entry%ROWTYPE;
BEGIN
    IF NEW.reversal_of IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT * INTO original FROM journal_entry WHERE id = NEW.reversal_of;
    IF NOT FOUND THEN
        -- La clave foránea reporta el error.
        RETURN NEW;
    END IF;
    IF original.reversal_of IS NOT NULL THEN
        RAISE EXCEPTION 'El asiento % ya es un reverso y no se puede reversar', original.id
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.entry_date < original.entry_date THEN
        RAISE EXCEPTION 'El reverso (%) no puede tener fecha anterior al asiento original (%)',
            NEW.entry_date, original.entry_date
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER journal_entry_reversal_valid
    BEFORE INSERT ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION check_journal_entry_reversal();

-- Los asientos y sus líneas no se editan ni se borran: un error se corrige con un reverso.
CREATE FUNCTION reject_ledger_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'La tabla % solo admite inserciones: un asiento se corrige con un reverso', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER journal_entry_append_only
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER journal_entry_no_truncate
    BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER entry_line_append_only
    BEFORE UPDATE OR DELETE ON entry_line
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER entry_line_no_truncate
    BEFORE TRUNCATE ON entry_line
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();
