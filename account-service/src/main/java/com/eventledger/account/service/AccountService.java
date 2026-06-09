package com.eventledger.account.service;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Apply a transaction to an account. Uses an idempotency key to avoid
     * duplicate processing. Throws TransactionApplicationException on failure.
     */
    public void applyTransaction(String accountId, TransactionRequest request, String traceId) {
        logger.info("Applying transaction for account={} traceId={}", accountId, traceId);
        try {
            String idem = request.getIdempotencyKey();
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idem);
            meterRegistry.counter("transactions.attempt").increment();
            if (existing.isPresent()) {
                meterRegistry.counter("transactions.duplicate").increment();
                return;
            }

            Account account = accountRepository.findByAccountId(accountId).orElseGet(() -> {
                Account a = new Account(accountId);
                a.setBalance(BigDecimal.ZERO);
                a.setCreatedAt(Instant.now());
                a.setUpdatedAt(Instant.now());
                return accountRepository.save(a);
            });

            Transaction.TransactionType type = Transaction.TransactionType.valueOf(request.getType());
            BigDecimal amount = request.getAmount();

            Transaction tx = new Transaction(accountId, type, amount, request.getCurrency(), idem);
            tx.setCreatedAt(Instant.now());
            transactionRepository.save(tx);

            // update balance
            if (type == Transaction.TransactionType.CREDIT) {
                account.setBalance(account.getBalance().add(amount));
            } else {
                account.setBalance(account.getBalance().subtract(amount));
            }
            account.setUpdatedAt(Instant.now());
            accountRepository.save(account);
            meterRegistry.counter("transactions.applied").increment();
        } catch (RuntimeException e) {
            logger.error("Failed to apply transaction", e);
            throw new TransactionApplicationException("Failed to apply transaction", e);
        }
    }

     //Return account balance or zero when account does not exist.
    public BigDecimal getBalance(String accountId, String traceId) {
            return accountRepository.findByAccountId(accountId)
            .map(Account::getBalance)
            .orElse(BigDecimal.ZERO);
    }


    //Retrieve account or throw when not found.
    public Account getAccount(String accountId, String traceId) {
        logger.info("Retrieving account {} traceId={}", accountId, traceId);
        return accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }


     //Return transactions for an account ordered by creation time.
    public List<Transaction> getTransactions(String accountId, String traceId) {
        logger.info("Retrieving transactions for account={} traceId={}", accountId, traceId);
        return transactionRepository.findByAccountIdOrderByCreatedAt(accountId);
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

