package io.github.morganbyte.zpenz.api.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.usecase.ledger.CreateAccountUseCase;
import io.github.morganbyte.zpenz.usecase.ledger.GetBalanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

/** Rutas provisionales de la Fase 3 (ADR-0008). La Fase 4 fija el contrato con OpenAPI. */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final CreateAccountUseCase createAccount;
    private final GetBalanceUseCase getBalance;

    public AccountController(CreateAccountUseCase createAccount, GetBalanceUseCase getBalance) {
        this.createAccount = createAccount;
        this.getBalance = getBalance;
    }

    public record CreateAccountRequest(String name, String type) {
    }

    public record AccountResponse(UUID id, String name, AccountType type) {
        static AccountResponse of(Account account) {
            return new AccountResponse(account.id().value(), account.name(), account.type());
        }
    }

    public record BalanceResponse(UUID accountId, String balance) {
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@RequestBody CreateAccountRequest request) {
        Account account = createAccount.create(Fields.required(request.name(), "name"), typeOf(request.type()));
        return ResponseEntity.created(URI.create("/accounts/" + account.id().value()))
                .body(AccountResponse.of(account));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable("id") UUID id) {
        return new BalanceResponse(id, getBalance.balanceOf(new AccountId(id)).toBigDecimal().toPlainString());
    }

    private static AccountType typeOf(String type) {
        String value = Fields.required(type, "type");
        try {
            return AccountType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "El tipo de cuenta \"" + value + "\" no existe; use uno de " + Arrays.toString(AccountType.values()), e);
        }
    }
}
