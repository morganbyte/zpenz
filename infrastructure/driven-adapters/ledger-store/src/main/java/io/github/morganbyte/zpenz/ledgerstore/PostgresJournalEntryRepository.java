package io.github.morganbyte.zpenz.ledgerstore;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.EntryAlreadyReversedException;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
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

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /** Posición de un asiento en el orden de {@link #entriesOf}. Es lo que codifica el cursor. */
    private record Position(LocalDate date, OffsetDateTime recordedAt, UUID id) {

        String encode() {
            String raw = date + "|" + recordedAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Position decode(String cursor) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("partes");
                }
                return new Position(LocalDate.parse(parts[0]), OffsetDateTime.parse(parts[1]), UUID.fromString(parts[2]));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IllegalArgumentException("El cursor \"" + cursor + "\" no es válido", e);
            }
        }
    }

    private record Row(Position position, String description, UUID reversalOf) {
    }

    /**
     * Pide un asiento más que el límite para saber si hay otra página. La comparación de filas
     * {@code (fecha, registro, id) < (...)} sigue el índice {@code journal_entry_newest_first_idx}.
     */
    @Override
    public EntryPage entriesOf(AccountId accountId, String after, int limit) {
        EntryPage.checkLimit(limit);
        Position from = after == null ? null : Position.decode(after);
        List<Row> rows = jdbc.sql("""
                        SELECT je.id, je.entry_date, je.recorded_at, je.description, je.reversal_of
                          FROM journal_entry je
                         WHERE EXISTS (SELECT 1 FROM entry_line l
                                        WHERE l.journal_entry_id = je.id AND l.account_id = :accountId)
                           AND (CAST(:hasCursor AS BOOLEAN) = FALSE
                                OR (je.entry_date, je.recorded_at, je.id)
                                   < (CAST(:date AS DATE), CAST(:recordedAt AS TIMESTAMPTZ), CAST(:id AS UUID)))
                         ORDER BY je.entry_date DESC, je.recorded_at DESC, je.id DESC
                         LIMIT :limit""")
                .param("accountId", accountId.value())
                .param("hasCursor", from != null)
                .param("date", from == null ? null : from.date(), Types.DATE)
                .param("recordedAt", from == null ? null : from.recordedAt(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", from == null ? null : from.id(), Types.OTHER)
                .param("limit", limit + 1)
                .query((rs, row) -> new Row(
                        new Position(rs.getObject("entry_date", LocalDate.class),
                                rs.getObject("recorded_at", OffsetDateTime.class),
                                rs.getObject("id", UUID.class)),
                        rs.getString("description"),
                        rs.getObject("reversal_of", UUID.class)))
                .list();
        boolean more = rows.size() > limit;
        List<Row> page = more ? rows.subList(0, limit) : rows;
        Map<UUID, List<EntryLine>> lines = linesOf(page.stream().map(r -> r.position().id()).toList());
        List<JournalEntry> entries = new ArrayList<>();
        for (Row r : page) {
            entries.add(JournalEntry.restore(new JournalEntryId(r.position().id()), r.position().date(), r.description(),
                    lines.get(r.position().id()), r.reversalOf() == null ? null : new JournalEntryId(r.reversalOf())));
        }
        return new EntryPage(entries, more ? Optional.of(page.getLast().position().encode()) : Optional.empty());
    }

    private Map<UUID, List<EntryLine>> linesOf(List<UUID> ids) {
        Map<UUID, List<EntryLine>> lines = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return lines;
        }
        jdbc.sql("""
                        SELECT journal_entry_id, account_id, side, amount
                          FROM entry_line
                         WHERE journal_entry_id IN (:ids)
                         ORDER BY journal_entry_id, line_number""")
                .param("ids", ids)
                .query(rs -> {
                    lines.computeIfAbsent(rs.getObject("journal_entry_id", UUID.class), k -> new ArrayList<>())
                            .add(new EntryLine(
                                    new AccountId(rs.getObject("account_id", UUID.class)),
                                    Side.valueOf(rs.getString("side")),
                                    Money.of(rs.getBigDecimal("amount"))));
                });
        return lines;
    }
}
