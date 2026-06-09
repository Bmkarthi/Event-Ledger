package com.eventledger.account.controller;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.service.AccountService;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.common.logging.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);
    private static final String SERVICE_NAME = "account-service";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<?> applyTransaction(@PathVariable String accountId,
                                              @RequestBody TransactionRequest request,
                                              @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("POST /accounts/{}/transactions - Applying transaction with traceId: {}", accountId, finalTraceId);
        StructuredLogger.logInfo(SERVICE_NAME, "POST /accounts - Applying transaction", finalTraceId,
            Map.of("accountId", accountId, "idempotencyKey", request.getIdempotencyKey()));

        try {
            accountService.applyTransaction(accountId, request, finalTraceId);
            StructuredLogger.logInfo(SERVICE_NAME, "Transaction applied successfully", finalTraceId,
                Map.of("accountId", accountId));
            logger.info("Transaction applied successfully for account: {}", accountId);
            return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "status", "SUCCESS",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (AccountService.TransactionApplicationException e) {
            StructuredLogger.logError(SERVICE_NAME, "Transaction failed: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            logger.error("Transaction failed for account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Transaction failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Unexpected error: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            logger.error("Unexpected error applying transaction for account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId,
                                        @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /accounts/{}/balance - Retrieving balance with traceId: {}", accountId, finalTraceId);
        StructuredLogger.logInfo(SERVICE_NAME, "GET /accounts - Retrieving balance", finalTraceId,
            Map.of("accountId", accountId));

        try {
            BigDecimal balance = accountService.getBalance(accountId, finalTraceId);
            logger.info("Balance retrieved for account: {}, balance: {}", accountId, balance);
            return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "balance", balance,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving balance: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            logger.error("Error retrieving balance for account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId,
                                        @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /accounts/{} - Retrieving account details with traceId: {}", accountId, finalTraceId);
        StructuredLogger.logInfo(SERVICE_NAME, "GET /accounts - Retrieving account details", finalTraceId,
            Map.of("accountId", accountId));

        try {
            Account account = accountService.getAccount(accountId, finalTraceId);
            List<Transaction> transactions = accountService.getTransactions(accountId, finalTraceId);

            logger.info("Account details retrieved for account: {}, transactions: {}", accountId, transactions.size());
            return ResponseEntity.ok(Map.of(
                "accountId", account.getAccountId(),
                "balance", account.getBalance(),
                "createdAt", account.getCreatedAt(),
                "updatedAt", account.getUpdatedAt(),
                "transactionCount", transactions.size(),
                "traceId", finalTraceId
            ));
        } catch (AccountService.AccountNotFoundException e) {
            StructuredLogger.logInfo(SERVICE_NAME, "Account not found", finalTraceId, Map.of("accountId", accountId));
            logger.info("Account not found: {}", accountId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Account not found",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving account: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            logger.error("Error retrieving account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        logger.info("Health check requested");
        StructuredLogger.logInfo(SERVICE_NAME, "Health check", null, null);
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "account-service",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
