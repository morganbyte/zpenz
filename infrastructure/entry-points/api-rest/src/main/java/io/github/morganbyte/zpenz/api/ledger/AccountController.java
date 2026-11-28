package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.api.ledger.Responses.AccountListResponse;
import io.github.morganbyte.zpenz.api.ledger.Responses.AccountResponse;
import io.github.morganbyte.zpenz.api.ledger.Responses.EntryPageResponse;
import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountBalance;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.EntryPage;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.money.Money;
import io.github.morganbyte.zpenz.usecase.ledger.CreateAccountUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.Idempotent;
import io.github.morganbyte.zpenz.usecase.ledger.QueryAccountsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(ApiV1.BASE + "/accounts")
@Tag(name = "Cuentas", description = "Cuentas del libro. Una cuenta no guarda saldo: se calcula desde los asientos.")
public class AccountController {

    private final CreateAccountUseCase createAccount;
    private final QueryAccountsUseCase queries;

    public AccountController(CreateAccountUseCase createAccount, QueryAccountsUseCase queries) {
        this.createAccount = createAccount;
        this.queries = queries;
    }

    @Schema(name = "CreateAccountRequest")
    public record CreateAccountRequest(
            @NotBlank(message = "es obligatorio") @Size(max = Account.NAME_MAX_LENGTH, message = "no puede pasar de {max} caracteres")
            @Schema(example = "Cuenta de ahorros") String name,
            @NotNull(message = "es obligatorio") AccountType type) {
    }

    @PostMapping
    @Operation(operationId = "createAccount", summary = "Crear una cuenta", description = "Toda cuenta empieza con saldo cero.")
    @ApiResponse(responseCode = "201", description = "Cuenta creada, o la misma cuenta si la petición se repite con la misma clave.",
            headers = {
            @Header(name = "Location", description = "Ruta del recurso creado.", schema = @Schema(type = "string")),
            @Header(name = ApiV1.REPLAYED, description = "`true` si es la repetición de una petición ya registrada.",
                    schema = @Schema(type = "boolean"))})
    @ApiResponse(responseCode = "400", description = "`validation-failed` o `invalid-request`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "`idempotency-key-reused`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AccountResponse> create(
            @Parameter(description = ApiV1.KEY_DESCRIPTION, required = true)
            @RequestHeader(ApiV1.IDEMPOTENCY_KEY) @Pattern(regexp = ApiV1.KEY_PATTERN, message = ApiV1.KEY_MESSAGE) String key,
            @Valid @RequestBody CreateAccountRequest request) {
        Idempotent<Account> result = createAccount.create(new IdempotencyKey(key), request.name(), request.type());
        Account account = result.value();
        AccountBalance balance = result.replayed() ? queries.get(account.id()) : new AccountBalance(account, Money.ZERO);
        return ResponseEntity.created(URI.create(ApiV1.BASE + "/accounts/" + account.id().value()))
                .header(ApiV1.REPLAYED, String.valueOf(result.replayed()))
                .body(AccountResponse.of(balance));
    }

    @GetMapping
    @Operation(operationId = "listAccounts", summary = "Listar las cuentas", description = "Todas las cuentas con su saldo, en el orden en que se crearon.")
    public AccountListResponse list() {
        return new AccountListResponse(queries.list().stream().map(AccountResponse::of).toList());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAccount", summary = "Ver una cuenta con su saldo")
    @ApiResponse(responseCode = "200", description = "La cuenta.")
    @ApiResponse(responseCode = "404", description = "`account-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public AccountResponse get(@PathVariable("id") UUID id) {
        return AccountResponse.of(queries.get(new AccountId(id)));
    }

    @GetMapping("/{id}/entries")
    @Operation(operationId = "listAccountEntries", summary = "Ver los asientos de una cuenta",
            description = "Del más reciente al más antiguo, por fecha y, en la misma fecha, por orden de registro. "
                    + "Para la página siguiente, repita la consulta con `cursor` igual al `nextCursor` recibido.")
    @ApiResponse(responseCode = "200", description = "Una página de asientos.")
    @ApiResponse(responseCode = "400", description = "`validation-failed` o `invalid-request` (cursor inválido).",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "`account-not-found`.",
            content = @Content(mediaType = ApiV1.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public EntryPageResponse entries(
            @PathVariable("id") UUID id,
            @Parameter(description = "El `nextCursor` de la página anterior. Vacío para la primera.")
            @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "Asientos por página, de 1 a " + EntryPage.MAX_LIMIT + ".")
            @RequestParam(name = "limit", defaultValue = "" + EntryPage.DEFAULT_LIMIT)
            @Min(value = 1, message = "debe ser al menos {value}") @Max(value = EntryPage.MAX_LIMIT, message = "no puede pasar de {value}")
            int limit) {
        return EntryPageResponse.of(queries.entries(new AccountId(id), cursor, limit));
    }
}
