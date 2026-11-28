package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.Side;
import io.github.morganbyte.zpenz.model.ledger.Transfer;
import io.github.morganbyte.zpenz.model.money.Money;
import io.github.morganbyte.zpenz.usecase.ledger.TransferUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/** Ruta provisional de la Fase 3 (ADR-0008). La Fase 4 fija el contrato con OpenAPI. */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final TransferUseCase transfers;

    public TransferController(TransferUseCase transfers) {
        this.transfers = transfers;
    }

    public record TransferRequest(UUID from, UUID to, BigDecimal amount, LocalDate date, String description) {

        Transfer toTransfer() {
            return new Transfer(
                    new AccountId(Fields.required(from, "from")),
                    new AccountId(Fields.required(to, "to")),
                    Money.of(Fields.required(amount, "amount")),
                    Fields.required(date, "date"),
                    Fields.required(description, "description"));
        }
    }

    public record TransferResponse(UUID id, UUID from, UUID to, String amount, LocalDate date, String description) {

        static TransferResponse of(JournalEntry entry) {
            UUID from = null;
            UUID to = null;
            for (var line : entry.lines()) {
                if (line.side() == Side.CREDIT) {
                    from = line.accountId().value();
                } else {
                    to = line.accountId().value();
                }
            }
            return new TransferResponse(entry.id().value(), from, to,
                    entry.total(Side.DEBIT).toBigDecimal().toPlainString(), entry.date(), entry.description());
        }
    }

    /**
     * Repetir la petición con la misma clave responde lo mismo que la original, con
     * {@code Idempotent-Replayed: true}, y no registra nada.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@RequestHeader(IDEMPOTENCY_KEY) String key,
                                                     @RequestBody TransferRequest request) {
        TransferUseCase.Result result = transfers.transfer(new IdempotencyKey(key), request.toTransfer());
        return ResponseEntity.created(URI.create("/transfers/" + result.entry().id().value()))
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.of(result.entry()));
    }
}
