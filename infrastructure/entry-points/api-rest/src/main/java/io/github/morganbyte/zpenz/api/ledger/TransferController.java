package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.api.ledger.Responses.TransferResponse;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.Transfer;
import io.github.morganbyte.zpenz.model.money.Money;
import io.github.morganbyte.zpenz.usecase.ledger.Idempotent;
import io.github.morganbyte.zpenz.usecase.ledger.TransferUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(ApiV1.BASE + "/transfers")
@Tag(name = "Transferencias", description = "Mover dinero entre cuentas (ADR-0008).")
public class TransferController {

    private final TransferUseCase transfers;

    public TransferController(TransferUseCase transfers) {
        this.transfers = transfers;
    }

    @Schema(name = "TransferRequest")
    public record TransferRequest(
            @NotNull(message = "es obligatoria") @Schema(description = "Cuenta de origen: recibe el crédito.") UUID from,
            @NotNull(message = "es obligatoria") @Schema(description = "Cuenta de destino: recibe el débito.") UUID to,
            @NotNull(message = "es obligatorio") @Pattern(regexp = ApiV1.AMOUNT_PATTERN, message = ApiV1.AMOUNT_MESSAGE)
            @Schema(example = "70000.00") String amount,
            @NotNull(message = "es obligatoria") LocalDate date,
            @NotBlank(message = "es obligatoria") @Size(max = JournalEntry.DESCRIPTION_MAX_LENGTH, message = "no puede pasar de {max} caracteres")
            @Schema(example = "Retiro") String description) {

        Transfer toTransfer() {
            return new Transfer(new AccountId(from), new AccountId(to), Money.of(amount), date, description);
        }
    }

    @PostMapping
    @Operation(operationId = "createTransfer", summary = "Registrar una transferencia",
            description = "Un asiento de dos líneas: crédito al origen y débito al destino. No puede dejar en negativo "
                    + "una cuenta de activo, aunque lleguen varias al mismo tiempo. El asiento se consulta en "
                    + "`/api/v1/journal-entries/{id}`.")
    @ApiResponse(responseCode = "201", description = "Transferencia registrada, o la misma si la petición se repite con la misma clave.",
            headers = {
            @Header(name = "Location", description = "Ruta del recurso creado.", schema = @Schema(type = "string")),
            @Header(name = ApiV1.REPLAYED, description = "`true` si es la repetición de una petición ya registrada.",
                    schema = @Schema(type = "boolean"))})
    @ApiResponse(responseCode = "400", description = "`validation-failed` o `invalid-request` (por ejemplo, origen igual al destino).",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`account-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "`insufficient-funds` o `idempotency-key-reused`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransferResponse> transfer(
            @Parameter(description = ApiV1.KEY_DESCRIPTION, required = true)
            @RequestHeader(ApiV1.IDEMPOTENCY_KEY) @Pattern(regexp = ApiV1.KEY_PATTERN, message = ApiV1.KEY_MESSAGE) String key,
            @Valid @RequestBody TransferRequest request) {
        Idempotent<JournalEntry> result = transfers.transfer(new IdempotencyKey(key), request.toTransfer());
        return ResponseEntity.created(URI.create(ApiV1.BASE + "/journal-entries/" + result.value().id().value()))
                .header(ApiV1.REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.of(result.value()));
    }
}
