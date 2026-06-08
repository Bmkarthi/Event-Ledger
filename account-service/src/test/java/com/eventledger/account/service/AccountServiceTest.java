package com.eventledger.account.service;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private AccountService accountService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    }

    @Test
    void applyTransaction_createsAccountAndAppliesCredit() {
        String accountId = "acct-111";
        TransactionRequest req = new TransactionRequest("CREDIT", new BigDecimal("100.00"), "USD", "idem-1");

        when(transactionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.applyTransaction(accountId, req, "trace-1");

        verify(transactionRepository).save(any(Transaction.class));
        verify(accountRepository, atLeastOnce()).save(accountCaptor.capture());

        Account saved = accountCaptor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(counter, atLeastOnce()).increment();
    }

    @Test
    void applyTransaction_idempotent_skipsProcessing() {
        String accountId = "acct-dup";
        TransactionRequest req = new TransactionRequest("CREDIT", new BigDecimal("50.00"), "USD", "idem-dup");

        Transaction existing = new Transaction(accountId, Transaction.TransactionType.CREDIT, new BigDecimal("50.00"), "USD", "idem-dup");
        when(transactionRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

        accountService.applyTransaction(accountId, req, "trace-2");

        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(counter, atLeastOnce()).increment();
    }

    @Test
    void applyTransaction_debit_updatesBalance() {
        String accountId = "acct-222";
        TransactionRequest req = new TransactionRequest("DEBIT", new BigDecimal("30.00"), "USD", "idem-2");

        Account existingAccount = new Account(accountId);
        existingAccount.setBalance(new BigDecimal("200.00"));

        when(transactionRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.applyTransaction(accountId, req, "trace-3");

        verify(transactionRepository).save(any(Transaction.class));
        verify(accountRepository).save(accountCaptor.capture());
        Account saved = accountCaptor.getValue();
        assertThat(saved.getBalance()).isEqualByComparingTo(new BigDecimal("170.00"));
    }

    @Test
    void getBalance_nonExistent_returnsZero() {
        when(accountRepository.findByAccountId("no-such")).thenReturn(Optional.empty());
        BigDecimal bal = accountService.getBalance("no-such", "t");
        assertThat(bal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBalance_existing_returnsBalance() {
        Account a = new Account("acct-x");
        a.setBalance(new BigDecimal("123.45"));
        when(accountRepository.findByAccountId("acct-x")).thenReturn(Optional.of(a));
        BigDecimal bal = accountService.getBalance("acct-x", "t");
        assertThat(bal).isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    void getAccount_nonExistent_throws() {
        when(accountRepository.findByAccountId("none")).thenReturn(Optional.empty());
        assertThrows(AccountService.AccountNotFoundException.class, () -> accountService.getAccount("none", "t"));
    }

    @Test
    void getTransactions_returnsList() {
        Transaction t1 = new Transaction("acct-a", Transaction.TransactionType.CREDIT, new BigDecimal("10"), "USD", "i1");
        t1.setCreatedAt(Instant.now());
        List<Transaction> txs = List.of(t1);
        when(transactionRepository.findByAccountIdOrderByCreatedAt("acct-a")).thenReturn(txs);
        List<Transaction> out = accountService.getTransactions("acct-a", "t");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getIdempotencyKey()).isEqualTo("i1");
    }

}


