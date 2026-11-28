package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotentOperation;
import io.github.morganbyte.zpenz.model.ledger.gateways.IdempotencyKeyRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final JdbcClient jdbc;

    public PostgresIdempotencyKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Si otra transacción insertó la misma clave y no ha terminado, el {@code INSERT} espera.
     * Si esa transacción confirma, no se inserta nada y la consulta siguiente, en
     * {@code READ COMMITTED}, ya ve su fila. Si se deshace, el {@code INSERT} reclama la clave.
     */
    @Override
    public Optional<Claim> claim(IdempotencyKey key, IdempotentOperation operation, UUID resourceId) {
        boolean account = operation.producesAccount();
        int claimed = jdbc.sql("""
                        INSERT INTO idempotency_key (key, operation, account_id, journal_entry_id)
                        VALUES (:key, :operation, :accountId, :entryId)
                        ON CONFLICT (key) DO NOTHING""")
                .param("key", key.value())
                .param("operation", operation.name())
                .param("accountId", account ? resourceId : null, Types.OTHER)
                .param("entryId", account ? null : resourceId, Types.OTHER)
                .update();
        if (claimed == 1) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                        SELECT operation, coalesce(account_id, journal_entry_id) AS resource_id
                          FROM idempotency_key
                         WHERE key = :key""")
                .param("key", key.value())
                .query((rs, row) -> new Claim(
                        IdempotentOperation.valueOf(rs.getString("operation")),
                        rs.getObject("resource_id", UUID.class)))
                .single());
    }
}
