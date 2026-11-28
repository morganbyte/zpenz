package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.model.ledger.AccountBalance;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.Side;
import io.github.morganbyte.zpenz.model.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cuerpos de respuesta de la API v1. Los montos van como texto con dos decimales (ADR-0009). */
final class Responses {

    private Responses() {
    }

    static String amount(Money money) {
        return money.toBigDecimal().toPlainString();
    }

    @Schema(name = "Account", description = "Una cuenta con su saldo calculado en el momento de la consulta.")
    record AccountResponse(
            UUID id,
            String name,
            AccountType type,
            @Schema(description = "Saldo en su lado normal. Puede ser negativo.", example = "30000.00") String balance) {

        static AccountResponse of(AccountBalance account) {
            return new AccountResponse(account.account().id().value(), account.account().name(),
                    account.account().type(), amount(account.balance()));
        }
    }

    @Schema(name = "AccountList")
    record AccountListResponse(List<AccountResponse> items) {
    }

    @Schema(name = "EntryLine")
    record LineResponse(UUID accountId, Side side, @Schema(example = "70000.00") String amount) {

        static LineResponse of(EntryLine line) {
            return new LineResponse(line.accountId().value(), line.side(), Responses.amount(line.amount()));
        }
    }

    @Schema(name = "JournalEntry", description = "Un asiento de doble partida. Es inmutable.")
    record EntryResponse(
            UUID id,
            LocalDate date,
            String description,
            @Schema(description = "El asiento que este reversa, si es un reverso.", nullable = true) UUID reversalOf,
            List<LineResponse> lines) {

        static EntryResponse of(JournalEntry entry) {
            return new EntryResponse(entry.id().value(), entry.date(), entry.description(),
                    entry.reversalOf().map(JournalEntryId::value).orElse(null),
                    entry.lines().stream().map(LineResponse::of).toList());
        }
    }

    @Schema(name = "JournalEntryPage")
    record EntryPageResponse(
            List<EntryResponse> items,
            @Schema(description = "Cursor de la página siguiente, o null si no hay más.", nullable = true)
            String nextCursor) {

        static EntryPageResponse of(EntryPage page) {
            return new EntryPageResponse(page.entries().stream().map(EntryResponse::of).toList(),
                    page.nextCursor().orElse(null));
        }
    }

    @Schema(name = "Transfer")
    record TransferResponse(UUID id, UUID from, UUID to, @Schema(example = "70000.00") String amount,
                            LocalDate date, String description) {

        static TransferResponse of(JournalEntry entry) {
            UUID from = null;
            UUID to = null;
            for (EntryLine line : entry.lines()) {
                if (line.side() == Side.CREDIT) {
                    from = line.accountId().value();
                } else {
                    to = line.accountId().value();
                }
            }
            return new TransferResponse(entry.id().value(), from, to, Responses.amount(entry.total(Side.DEBIT)),
                    entry.date(), entry.description());
        }
    }
}
