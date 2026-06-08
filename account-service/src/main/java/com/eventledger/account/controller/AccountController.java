package com.eventledger.account.controller;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoint for account management
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * POST /accounts/{accountId}/transactions - Apply a transaction to an account
     */
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<?> applyTransaction(@PathVariable String accountId,
                                              @RequestBody TransactionRequest request,
                                              @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("POST /accounts/{}/transactions - Applying transaction [traceId={}]", accountId, finalTraceId);

        try {
            accountService.applyTransaction(accountId, request, finalTraceId);
            logger.info("POST /accounts/{}/transactions - Transaction applied successfully [traceId={}]", accountId, finalTraceId);
            return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "status", "SUCCESS",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (AccountService.TransactionApplicationException e) {
            logger.error("POST /accounts/{}/transactions - Transaction failed [traceId={}]: {}", accountId, finalTraceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Transaction failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            logger.error("POST /accounts/{}/transactions - Unexpected error [traceId={}]", accountId, finalTraceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    /**
     * GET /accounts/{accountId}/balance - Get the current balance
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId,
                                        @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /accounts/{}/balance - Retrieving balance [traceId={}]", accountId, finalTraceId);

        try {
            BigDecimal balance = accountService.getBalance(accountId, finalTraceId);
            return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "balance", balance,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            logger.error("GET /accounts/{}/balance - Error [traceId={}]", accountId, finalTraceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    /**
     * GET /accounts/{accountId} - Get account details
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId,
                                        @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /accounts/{} - Retrieving account details [traceId={}]", accountId, finalTraceId);

        try {
            Account account = accountService.getAccount(accountId, finalTraceId);
            List<Transaction> transactions = accountService.getTransactions(accountId, finalTraceId);

            return ResponseEntity.ok(Map.of(
                "accountId", account.getAccountId(),
                "balance", account.getBalance(),
                "createdAt", account.getCreatedAt(),
                "updatedAt", account.getUpdatedAt(),
                "transactionCount", transactions.size(),
                "traceId", finalTraceId
            ));
        } catch (AccountService.AccountNotFoundException e) {
            logger.warn("GET /accounts/{} - Account not found [traceId={}]", accountId, finalTraceId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Account not found",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            logger.error("GET /accounts/{} - Error [traceId={}]", accountId, finalTraceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    /**
     * GET /health - Health check
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "account-service",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

