package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.api.ledger.Responses.EntryResponse;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.EntryLine;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.JournalEntryId;
import io.github.morganbyte.zpenz.model.ledger.Side;
import io.github.morganbyte.zpenz.model.money.Money;
import io.github.morganbyte.zpenz.usecase.ledger.GetJournalEntryUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.Idempotent;
import io.github.morganbyte.zpenz.usecase.ledger.PostJournalEntryUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.ReverseJournalEntryUseCase;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiV1.BASE + "/journal-entries")
@Tag(name = "Asientos", description = "Asientos de doble partida. No se editan ni se borran: un error se corrige con un reverso.")
public class JournalEntryController {

    private final PostJournalEntryUseCase postEntry;
    private final GetJournalEntryUseCase getEntry;
    private final ReverseJournalEntryUseCase reverseEntry;

    public JournalEntryController(PostJournalEntryUseCase postEntry, GetJournalEntryUseCase getEntry,
                                  ReverseJournalEntryUseCase reverseEntry) {
        this.postEntry = postEntry;
        this.getEntry = getEntry;
        this.reverseEntry = reverseEntry;
    }

    @Schema(name = "EntryLineRequest")
    public record LineRequest(
            @NotNull(message = "es obligatorio") UUID accountId,
            @NotNull(message = "es obligatorio") Side side,
            @NotNull(message = "es obligatorio") @Pattern(regexp = ApiV1.AMOUNT_PATTERN, message = ApiV1.AMOUNT_MESSAGE)
            @Schema(example = "70000.00") String amount) {

        EntryLine toLine() {
            return new EntryLine(new AccountId(accountId), side, Money.of(amount));
        }
    }

    @Schema(name = "PostJournalEntryRequest")
    public record PostEntryRequest(
            @NotNull(message = "es obligatoria") LocalDate date,
            @NotBlank(message = "es obligatoria") @Size(max = JournalEntry.DESCRIPTION_MAX_LENGTH, message = "no puede pasar de {max} caracteres")
            String description,
            @NotNull(message = "son obligatorias") @Size(min = 2, max = 100, message = "deben ser de {min} a {max}")
            List<@NotNull(message = "no puede ser nula") @Valid LineRequest> lines) {
    }

    @Schema(name = "ReverseJournalEntryRequest")
    public record ReverseRequest(
            @NotNull(message = "es obligatoria") LocalDate date,
            @NotBlank(message = "es obligatoria") @Size(max = JournalEntry.DESCRIPTION_MAX_LENGTH, message = "no puede pasar de {max} caracteres")
            @Schema(example = "Reverso: mercado duplicado") String description) {
    }

    @PostMapping
    @Operation(operationId = "postJournalEntry", summary = "Registrar un asiento",
            description = "Dos o más líneas, con al menos un débito y un crédito, cuyos débitos y créditos sumen lo mismo.")
    @ApiResponse(responseCode = "201", description = "Asiento registrado, o el mismo si la petición se repite con la misma clave.",
            headers = {
            @Header(name = "Location", description = "Ruta del recurso creado.", schema = @Schema(type = "string")),
            @Header(name = ApiV1.REPLAYED, description = "`true` si es la repetición de una petición ya registrada.",
                    schema = @Schema(type = "boolean"))})
    @ApiResponse(responseCode = "400", description = "`validation-failed` o `invalid-request` (por ejemplo, un asiento que no cuadra).",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`account-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "`idempotency-key-reused`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EntryResponse> post(
            @Parameter(description = ApiV1.KEY_DESCRIPTION, required = true)
            @RequestHeader(ApiV1.IDEMPOTENCY_KEY) @Pattern(regexp = ApiV1.KEY_PATTERN, message = ApiV1.KEY_MESSAGE) String key,
            @Valid @RequestBody PostEntryRequest request) {
        Idempotent<JournalEntry> result = postEntry.post(new IdempotencyKey(key), request.date(), request.description(),
                request.lines().stream().map(LineRequest::toLine).toList());
        return created(result);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getJournalEntry", summary = "Ver un asiento con sus líneas")
    @ApiResponse(responseCode = "200", description = "El asiento.")
    @ApiResponse(responseCode = "404", description = "`entry-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public EntryResponse get(@PathVariable("id") UUID id) {
        return EntryResponse.of(getEntry.get(new JournalEntryId(id)));
    }

    @PostMapping("/{id}/reversal")
    @Operation(operationId = "reverseJournalEntry", summary = "Reversar un asiento",
            description = "Registra un asiento con las mismas líneas y los lados invertidos. El original no cambia. "
                    + "Un asiento se reversa una sola vez y un reverso no se reversa.")
    @ApiResponse(responseCode = "201", description = "Reverso registrado, o el mismo si la petición se repite con la misma clave.",
            headers = {
            @Header(name = "Location", description = "Ruta del recurso creado.", schema = @Schema(type = "string")),
            @Header(name = ApiV1.REPLAYED, description = "`true` si es la repetición de una petición ya registrada.",
                    schema = @Schema(type = "boolean"))})
    @ApiResponse(responseCode = "400", description = "`validation-failed` o `invalid-request` (por ejemplo, fecha anterior al original).",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`entry-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "`entry-already-reversed` o `entry-is-reversal`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "`idempotency-key-reused`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EntryResponse> reverse(
            @PathVariable("id") UUID id,
            @Parameter(description = ApiV1.KEY_DESCRIPTION, required = true)
            @RequestHeader(ApiV1.IDEMPOTENCY_KEY) @Pattern(regexp = ApiV1.KEY_PATTERN, message = ApiV1.KEY_MESSAGE) String key,
            @Valid @RequestBody ReverseRequest request) {
        return created(reverseEntry.reverse(new IdempotencyKey(key), new JournalEntryId(id), request.date(), request.description()));
    }

    private static ResponseEntity<EntryResponse> created(Idempotent<JournalEntry> result) {
        return ResponseEntity.created(URI.create(ApiV1.BASE + "/journal-entries/" + result.value().id().value()))
                .header(ApiV1.REPLAYED, String.valueOf(result.replayed()))
                .body(EntryResponse.of(result.value()));
    }
}
