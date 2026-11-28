package io.github.morganbyte.zpenz.usecase.ledger;

import io.github.morganbyte.zpenz.model.ledger.Account;
import io.github.morganbyte.zpenz.model.ledger.AccountId;
import io.github.morganbyte.zpenz.model.ledger.AccountNotFoundException;
import io.github.morganbyte.zpenz.model.ledger.AccountType;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKey;
import io.github.morganbyte.zpenz.model.ledger.IdempotencyKeyReusedException;
import io.github.morganbyte.zpenz.model.ledger.InsufficientFundsException;
import io.github.morganbyte.zpenz.model.ledger.JournalEntry;
import io.github.morganbyte.zpenz.model.ledger.Transfer;
import io.github.morganbyte.zpenz.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.github.morganbyte.zpenz.model.ledger.EntryLine.credit;
import static io.github.morganbyte.zpenz.model.ledger.EntryLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final CreateAccountUseCase createAccount = new CreateAccountUseCase(ledger, ledger, ledger);
    private final PostJournalEntryUseCase postEntry = new PostJournalEntryUseCase(ledger, ledger, ledger, ledger);
    private final GetBalanceUseCase getBalance = new GetBalanceUseCase(ledger, ledger);
    private final TransferUseCase transfers = new TransferUseCase(ledger, ledger, ledger, ledger);

    private final Account savings = createAccount.create("Cuenta de ahorros", AccountType.ASSET);
    private final Account cash = createAccount.create("Efectivo", AccountType.ASSET);
    private final Account card = createAccount.create("Tarjeta de crédito", AccountType.LIABILITY);
    private final Account salary = createAccount.create("Salario", AccountType.INCOME);

    private static IdempotencyKey newKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    private static Transfer transfer(Account from, Account to, String amount) {
        return new Transfer(from.id(), to.id(), Money.of(amount), DATE, "Retiro");
    }

    private void deposit(Account account, String amount) {
        postEntry.post(DATE, "Salario",
                List.of(debit(account.id(), Money.of(amount)), credit(salary.id(), Money.of(amount))));
    }

    @Test
    void movesMoneyBetweenAccountsInOneTransaction() {
        deposit(savings, "100000");

        Idempotent<JournalEntry> result = transfers.transfer(newKey(), transfer(savings, cash, "70000"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.value().lines()).containsExactly(
                debit(cash.id(), Money.of("70000")), credit(savings.id(), Money.of("70000")));
        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.of("30000"));
        assertThat(getBalance.balanceOf(cash.id())).isEqualTo(Money.of("70000"));
        assertThat(ledger.transactionsStarted).isEqualTo(1);
    }

    @Test
    void allowsEmptyingAnAssetAccount() {
        deposit(savings, "100000");

        transfers.transfer(newKey(), transfer(savings, cash, "100000"));

        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsLeavingAnAssetAccountNegativeAndRecordsNothing() {
        deposit(savings, "100000");
        IdempotencyKey key = newKey();
        int entriesBefore = ledger.entries.size();

        assertThatThrownBy(() -> transfers.transfer(key, transfer(savings, cash, "100000.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("100000.00 COP");

        assertThat(ledger.entries).hasSize(entriesBefore);
        assertThat(ledger.keys).doesNotContainKey(key);
    }

    @Test
    void aRejectedKeyCanBeRetriedOnceThereAreFunds() {
        IdempotencyKey key = newKey();
        assertThatThrownBy(() -> transfers.transfer(key, transfer(savings, cash, "50000")))
                .isInstanceOf(InsufficientFundsException.class);

        deposit(savings, "50000");

        assertThat(transfers.transfer(key, transfer(savings, cash, "50000")).replayed()).isFalse();
        assertThat(getBalance.balanceOf(cash.id())).isEqualTo(Money.of("50000"));
    }

    @Test
    void liabilitiesHaveNoLimit() {
        transfers.transfer(newKey(), transfer(card, cash, "300000"));

        assertThat(getBalance.balanceOf(card.id())).isEqualTo(Money.of("300000"));
        assertThat(getBalance.balanceOf(cash.id())).isEqualTo(Money.of("300000"));
    }

    @Test
    void repeatingTheSameRequestReturnsTheOriginalEntry() {
        deposit(savings, "100000");
        IdempotencyKey key = newKey();

        Idempotent<JournalEntry> first = transfers.transfer(key, transfer(savings, cash, "70000"));
        Idempotent<JournalEntry> second = transfers.transfer(key, transfer(savings, cash, "70000"));

        assertThat(second.replayed()).isTrue();
        assertThat(second.value()).isEqualTo(first.value());
        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.of("30000"));
    }

    @Test
    void aRepeatIsRecognisedEvenIfTheFundsAreGoneNow() {
        deposit(savings, "100000");
        IdempotencyKey key = newKey();
        transfers.transfer(key, transfer(savings, cash, "100000"));

        assertThat(transfers.transfer(key, transfer(savings, cash, "100000")).replayed()).isTrue();
    }

    @Test
    void rejectsReusingAKeyForADifferentTransfer() {
        deposit(savings, "100000");
        IdempotencyKey key = newKey();
        transfers.transfer(key, transfer(savings, cash, "70000"));

        assertThatThrownBy(() -> transfers.transfer(key, transfer(savings, cash, "10000")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(getBalance.balanceOf(savings.id())).isEqualTo(Money.of("30000"));
    }

    @Test
    void rejectsUnknownAccounts() {
        AccountId unknown = AccountId.random();
        Account ghost = new Account(unknown, "Fantasma", AccountType.ASSET);

        assertThatThrownBy(() -> transfers.transfer(newKey(), transfer(ghost, cash, "1")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(unknown.value().toString());
        assertThatThrownBy(() -> transfers.transfer(newKey(), transfer(card, ghost, "1")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(unknown.value().toString());
        assertThat(ledger.keys).isEmpty();
    }
}
