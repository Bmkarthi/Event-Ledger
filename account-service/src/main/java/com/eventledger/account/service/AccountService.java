package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing accounts and transactions with idempotency
 */
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
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

    /**
     * Apply a transaction to an account with idempotency
     */
    @Transactional
    public void applyTransaction(String accountId, TransactionRequest request, String traceId) {
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("accountId", accountId);
        logContext.put("type", request.getType());
        logContext.put("amount", request.getAmount());

        try {
            // Check for duplicate (idempotency)
            Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingTx.isPresent()) {
                logger.info("Duplicate transaction (idempotent) [traceId={}]", traceId);
                logContext.put("duplicate", true);
                meterRegistry.counter("transactions.duplicate").increment();
                return;
            }

            // Get or create account
            Account account = accountRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    logger.info("Creating new account: {} [traceId={}]", accountId, traceId);
                    Account newAccount = new Account(accountId);
                    return accountRepository.save(newAccount);
                });

            // Create transaction record
            Transaction transaction = new Transaction(
                accountId,
                Transaction.TransactionType.valueOf(request.getType()),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey()
            );

            // Update balance based on transaction type
            if ("CREDIT".equals(request.getType())) {
                account.setBalance(account.getBalance().add(request.getAmount()));
            } else if ("DEBIT".equals(request.getType())) {
                account.setBalance(account.getBalance().subtract(request.getAmount()));
            }

            account.setUpdatedAt(Instant.now());
            accountRepository.save(account);
            transactionRepository.save(transaction);

            logContext.put("newBalance", account.getBalance());
            logger.info("Transaction applied successfully [traceId={}]", traceId);
            meterRegistry.counter("transactions.applied").increment();

        } catch (Exception e) {
            logContext.put("error", e.getMessage());
            logger.error("Failed to apply transaction [traceId={}]", traceId, e);
            meterRegistry.counter("transactions.failed").increment();
            throw new TransactionApplicationException("Failed to apply transaction", e);
        }
    }

    /**
     * Get balance for an account
     */
    public BigDecimal getBalance(String accountId, String traceId) {
        Optional<Account> account = accountRepository.findByAccountId(accountId);

        if (account.isEmpty()) {
            logger.info("Account not found: {} [traceId={}]", accountId, traceId);
            // Return zero balance for non-existent account
            return BigDecimal.ZERO;
        }

        BigDecimal balance = account.get().getBalance();
        logger.info("Retrieved balance for account {} [traceId={}]: {}", accountId, traceId, balance);
        return balance;
    }

    /**
     * Get account details
     */
    public Account getAccount(String accountId, String traceId) {
        Optional<Account> account = accountRepository.findByAccountId(accountId);

        if (account.isEmpty()) {
            logger.info("Account not found: {} [traceId={}]", accountId, traceId);
            throw new AccountNotFoundException("Account not found: " + accountId);
        }

        return account.get();
    }

    /**
     * Get transactions for an account
     */
    public List<Transaction> getTransactions(String accountId, String traceId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAt(accountId);
        logger.info("Retrieved {} transactions for account {} [traceId={}]",
            transactions.size(), accountId, traceId);
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

