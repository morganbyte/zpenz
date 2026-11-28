package io.github.morganbyte.zpenz.model.ledger;

public class AccountNotFoundException extends RuntimeException {

    private final transient AccountId accountId;

    public AccountNotFoundException(AccountId accountId) {
        super("No existe la cuenta " + accountId.value());
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
