package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.Side;
import io.github.morganbyte.zpenz.model.ledger.SideTotals;
import io.github.morganbyte.zpenz.model.ledger.gateways.JournalEntryRepository;
import io.github.morganbyte.zpenz.model.money.Money;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresJournalEntryRepository implements JournalEntryRepository {

    /** Nombre de la restricción {@code UNIQUE (reversal_of)} en la migración V1. */
    static final String REVERSED_ONCE_CONSTRAINT = "journal_entry_reversed_once";

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public PostgresJournalEntryRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public void add(JournalEntry entry) {
        try {
            transaction.executeWithoutResult(status -> insert(entry));
        } catch (DuplicateKeyException e) {
            if (entry.reversalOf().isPresent() && violates(e, REVERSED_ONCE_CONSTRAINT)) {
                throw new EntryAlreadyReversedException(entry.reversalOf().get());
            }
            throw e;
        }
    }

    private void insert(JournalEntry entry) {
        jdbc.sql("""
                        INSERT INTO journal_entry (id, entry_date, description, reversal_of)
                        VALUES (:id, :date, :description, :reversalOf)""")
                .param("id", entry.id().value())
                .param("date", entry.date())
                .param("description", entry.description())
                .param("reversalOf", entry.reversalOf().map(JournalEntryId::value).orElse(null), Types.OTHER)
                .update();
        int lineNumber = 1;
        for (EntryLine line : entry.lines()) {
            jdbc.sql("""
                            INSERT INTO entry_line (journal_entry_id, line_number, account_id, side, amount)
                            VALUES (:entryId, :lineNumber, :accountId, :side, :amount)""")
                    .param("entryId", entry.id().value())
                    .param("lineNumber", lineNumber++)
                    .param("accountId", line.accountId().value())
                    .param("side", line.side().name())
                    .param("amount", line.amount().toBigDecimal())
                    .update();
        }
    }

    /** PostgreSQL nombra la restricción violada en el mensaje: {@code violates unique constraint "..."}. */
    private static boolean violates(DuplicateKeyException e, String constraint) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("\"" + constraint + "\"");
    }

    @Override
    public Optional<JournalEntry> findById(JournalEntryId id) {
        record Header(LocalDate date, String description, UUID reversalOf) {
        }
        Optional<Header> header = jdbc.sql("""
                        SELECT entry_date, description, reversal_of
                          FROM journal_entry
                         WHERE id = :id""")
                .param("id", id.value())
                .query((rs, row) -> new Header(
                        rs.getObject("entry_date", LocalDate.class),
                        rs.getString("description"),
                        rs.getObject("reversal_of", UUID.class)))
                .optional();
        return header.map(h -> JournalEntry.restore(id, h.date(), h.description(), linesOf(id),
                h.reversalOf() == null ? null : new JournalEntryId(h.reversalOf())));
    }

    private List<EntryLine> linesOf(JournalEntryId id) {
        return jdbc.sql("""
                        SELECT account_id, side, amount
                          FROM entry_line
                         WHERE journal_entry_id = :id
                         ORDER BY line_number""")
                .param("id", id.value())
                .query((rs, row) -> new EntryLine(
                        new AccountId(rs.getObject("account_id", UUID.class)),
                        Side.valueOf(rs.getString("side")),
                        Money.of(rs.getBigDecimal("amount"))))
                .list();
    }

    @Override
    public boolean isReversed(JournalEntryId id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM journal_entry WHERE reversal_of = :id)")
                .param("id", id.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public SideTotals totalsFor(AccountId accountId) {
        return jdbc.sql("""
                        SELECT coalesce(sum(amount) FILTER (WHERE side = 'DEBIT'), 0)  AS debits,
                               coalesce(sum(amount) FILTER (WHERE side = 'CREDIT'), 0) AS credits
                          FROM entry_line
                         WHERE account_id = :accountId""")
                .param("accountId", accountId.value())
                .query((rs, row) -> new SideTotals(
                        Money.of(rs.getBigDecimal("debits")),
                        Money.of(rs.getBigDecimal("credits"))))
                .single();
    }
}
