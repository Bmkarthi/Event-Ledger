package com.eventledger.account.controller;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.service.AccountService;
import com.eventledger.common.logging.StructuredLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {

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
        StructuredLogger.logInfo(SERVICE_NAME, "POST /accounts - Applying transaction", finalTraceId,
            Map.of("accountId", accountId, "idempotencyKey", request.getIdempotencyKey()));

        try {
            accountService.applyTransaction(accountId, request, finalTraceId);
            StructuredLogger.logInfo(SERVICE_NAME, "Transaction applied successfully", finalTraceId,
                Map.of("accountId", accountId));
            return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "status", "SUCCESS",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (AccountService.TransactionApplicationException e) {
            StructuredLogger.logError(SERVICE_NAME, "Transaction failed: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Transaction failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Unexpected error: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
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
        StructuredLogger.logInfo(SERVICE_NAME, "GET /accounts - Retrieving balance", finalTraceId,
            Map.of("accountId", accountId));

        try {
            BigDecimal balance = accountService.getBalance(accountId, finalTraceId);
            return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "balance", balance,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving balance: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
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
        StructuredLogger.logInfo(SERVICE_NAME, "GET /accounts - Retrieving account details", finalTraceId,
            Map.of("accountId", accountId));

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
            StructuredLogger.logInfo(SERVICE_NAME, "Account not found", finalTraceId, Map.of("accountId", accountId));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Account not found",
                "accountId", accountId,
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving account: " + e.getMessage(), finalTraceId,
                Map.of("accountId", accountId));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        StructuredLogger.logInfo(SERVICE_NAME, "Health check", null, null);
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "account-service",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

