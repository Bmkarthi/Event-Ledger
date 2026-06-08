package com.eventledger.account.service;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.common.logging.StructuredLogger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountService {

    private static final String SERVICE_NAME = "account-service";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                         MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void applyTransaction(String accountId, TransactionRequest request, String traceId) {
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("accountId", accountId);
        logContext.put("type", request.getType());
        logContext.put("amount", request.getAmount());

        try {
            
            Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingTx.isPresent()) {
                StructuredLogger.logInfo(SERVICE_NAME, "Duplicate transaction (idempotent)", traceId, Map.of("idempotencyKey", request.getIdempotencyKey()));
                logContext.put("duplicate", true);
                meterRegistry.counter("transactions.duplicate").increment();
                return;
            }

            
            Account account = accountRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    StructuredLogger.logInfo(SERVICE_NAME, "Creating new account", traceId, Map.of("accountId", accountId));
                    Account newAccount = new Account(accountId);
                    return accountRepository.save(newAccount);
                });

            
            Transaction transaction = new Transaction(
                accountId,
                Transaction.TransactionType.valueOf(request.getType()),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey()
            );

            
            if ("CREDIT".equals(request.getType())) {
                account.setBalance(account.getBalance().add(request.getAmount()));
            } else if ("DEBIT".equals(request.getType())) {
                account.setBalance(account.getBalance().subtract(request.getAmount()));
            }

            account.setUpdatedAt(Instant.now());
            accountRepository.save(account);
            transactionRepository.save(transaction);

            logContext.put("newBalance", account.getBalance());
            StructuredLogger.logInfo(SERVICE_NAME, "Transaction applied successfully", traceId, Map.of("accountId", accountId, "newBalance", account.getBalance()));
            meterRegistry.counter("transactions.applied").increment();

        } catch (Exception e) {
            logContext.put("error", e.getMessage());
            StructuredLogger.logError(SERVICE_NAME, "Failed to apply transaction: " + e.getMessage(), traceId, Map.of("accountId", accountId));
            meterRegistry.counter("transactions.failed").increment();
            throw new TransactionApplicationException("Failed to apply transaction", e);
        }
    }

    public BigDecimal getBalance(String accountId, String traceId) {
        Optional<Account> account = accountRepository.findByAccountId(accountId);

        if (account.isEmpty()) {
            StructuredLogger.logInfo(SERVICE_NAME, "Account not found", traceId, Map.of("accountId", accountId));
            // Return zero balance for non-existent account
            return BigDecimal.ZERO;
        }

        BigDecimal balance = account.get().getBalance();
        StructuredLogger.logInfo(SERVICE_NAME, "Retrieved balance for account", traceId, Map.of("accountId", accountId, "balance", balance));
        return balance;
    }

    public Account getAccount(String accountId, String traceId) {
        Optional<Account> account = accountRepository.findByAccountId(accountId);

        if (account.isEmpty()) {
            StructuredLogger.logInfo(SERVICE_NAME, "Account not found", traceId, Map.of("accountId", accountId));
            throw new AccountNotFoundException("Account not found: " + accountId);
        }

        return account.get();
    }

    public List<Transaction> getTransactions(String accountId, String traceId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAt(accountId);
        StructuredLogger.logInfo(SERVICE_NAME, "Retrieved transactions for account", traceId, Map.of("accountId", accountId, "count", transactions.size()));
        return transactions;
    }

    public static class TransactionApplicationException extends RuntimeException {
        public TransactionApplicationException(String message) {
            super(message);
        }

        public TransactionApplicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String message) {
            super(message);
        }
    }
}

