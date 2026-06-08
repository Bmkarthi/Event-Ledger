package com.eventledger.account.integration;

import com.eventledger.account.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Account Service
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testApplyTransactionAndBalance() {
        String accountId = "acct-balance-001";

        // Apply credit transaction
        TransactionRequest creditRequest = new TransactionRequest(
            "CREDIT", new BigDecimal("500.00"), "USD", "evt-txn-001"
        );

        ResponseEntity<?> creditResponse = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", creditRequest, Map.class);

        assertEquals(HttpStatus.OK, creditResponse.getStatusCode());

        // Check balance
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(HttpStatus.OK, balanceResponse.getStatusCode());
        assertNotNull(balanceResponse.getBody());
        assertEquals(500.0, balanceResponse.getBody().get("balance"));
    }

    @Test
    void testMultipleTransactions() {
        String accountId = "acct-multi-001";

        // CREDIT 100
        TransactionRequest credit1 = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-multi-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit1, Map.class);

        // CREDIT 50
        TransactionRequest credit2 = new TransactionRequest(
            "CREDIT", new BigDecimal("50.00"), "USD", "evt-multi-002"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit2, Map.class);

        // DEBIT 30
        TransactionRequest debit = new TransactionRequest(
            "DEBIT", new BigDecimal("30.00"), "USD", "evt-multi-003"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", debit, Map.class);

        // Balance should be 100 + 50 - 30 = 120
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(120.0, balanceResponse.getBody().get("balance"));
    }

    @Test
    void testIdempotencyOnTransactions() {
        String accountId = "acct-idem-txn-001";

        TransactionRequest request = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-idem-txn-001"
        );

        // First call
        ResponseEntity<?> response1 = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", request, Map.class);

        // Second call with same idempotency key
        ResponseEntity<?> response2 = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", request, Map.class);

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.OK, response2.getStatusCode());

        // Balance should only have been increased once
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(100.0, balanceResponse.getBody().get("balance"));
    }

    @Test
    void testGetAccountDetails() {
        String accountId = "acct-details-001";

        // Create account by applying transaction
        TransactionRequest request = new TransactionRequest(
            "CREDIT", new BigDecimal("250.00"), "USD", "evt-details-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", request, Map.class);

        // Get account details
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/" + accountId, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(accountId, response.getBody().get("accountId"));
        assertEquals(250.0, response.getBody().get("balance"));
        assertEquals(1, response.getBody().get("transactionCount"));
    }

    @Test
    void testHealthCheck() {
        ResponseEntity<?> response = restTemplate.getForEntity("/accounts/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testNonExistentAccountBalance() {
        // Non-existent account should return 0 balance
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/acct-nonexistent/balance", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0.0, response.getBody().get("balance"));
    }

    @Test
    void testNegativeBalance() {
        String accountId = "acct-negative-001";

        // Start with 100
        TransactionRequest credit = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-neg-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit, Map.class);

        // Debit 150 (goes negative)
        TransactionRequest debit = new TransactionRequest(
            "DEBIT", new BigDecimal("150.00"), "USD", "evt-neg-002"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", debit, Map.class);

        // Should allow negative balance
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(-50.0, response.getBody().get("balance"));
    }
}

