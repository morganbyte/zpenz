package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.gateways.AccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class PostgresAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    public PostgresAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Account account) {
        jdbc.sql("INSERT INTO account (id, name, type) VALUES (:id, :name, :type)")
                .param("id", account.id().value())
                .param("name", account.name())
                .param("type", account.type().name())
                .update();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.sql("SELECT id, name, type FROM account WHERE id = :id")
                .param("id", id.value())
                .query(PostgresAccountRepository::toAccount)
                .optional();
    }

    @Override
    public List<Account> findAll() {
        return jdbc.sql("SELECT id, name, type FROM account ORDER BY created_at, id")
                .query(PostgresAccountRepository::toAccount)
                .list();
    }

    private static Account toAccount(ResultSet rs, int row) throws SQLException {
        return new Account(
                new AccountId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")));
    }

    /**
     * {@code FOR NO KEY UPDATE} y no {@code FOR UPDATE}: insertar una línea que referencia la
     * cuenta toma {@code FOR KEY SHARE} por la clave foránea, que choca con {@code FOR UPDATE}
     * pero no con este bloqueo (ADR-0008).
     */
    @Override
    public Optional<Account> lockById(AccountId id) {
        return jdbc.sql("SELECT id, name, type FROM account WHERE id = :id FOR NO KEY UPDATE")
                .param("id", id.value())
                .query(PostgresAccountRepository::toAccount)
                .optional();
    }

    @Override
    public Set<AccountId> findMissing(Collection<AccountId> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<UUID> wanted = ids.stream().map(AccountId::value).distinct().toList();
        List<UUID> found = jdbc.sql("SELECT id FROM account WHERE id IN (:ids)")
                .param("ids", wanted)
                .query(UUID.class)
                .list();
        Set<AccountId> missing = new HashSet<>();
        for (UUID id : wanted) {
            if (!found.contains(id)) {
                missing.add(new AccountId(id));
            }
        }
        return missing;
    }
}
