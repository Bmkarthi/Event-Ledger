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


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testApplyTransactionAndBalance() {
        String accountId = "acct-balance-001";

        TransactionRequest creditRequest = new TransactionRequest(
            "CREDIT", new BigDecimal("500.00"), "USD", "evt-txn-001"
        );

        ResponseEntity<?> creditResponse = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", creditRequest, Map.class);

        assertEquals(HttpStatus.OK, creditResponse.getStatusCode());

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(HttpStatus.OK, balanceResponse.getStatusCode());
        assertNotNull(balanceResponse.getBody());
        Object balanceObj = balanceResponse.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(500.0, ((Number) balanceObj).doubleValue());
    }

    @Test
    void testMultipleTransactions() {
        String accountId = "acct-multi-001";

        TransactionRequest credit1 = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-multi-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit1, Map.class);

        TransactionRequest credit2 = new TransactionRequest(
            "CREDIT", new BigDecimal("50.00"), "USD", "evt-multi-002"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit2, Map.class);

        TransactionRequest debit = new TransactionRequest(
            "DEBIT", new BigDecimal("30.00"), "USD", "evt-multi-003"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", debit, Map.class);

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        Object balanceObj = balanceResponse.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(120.0, ((Number) balanceObj).doubleValue());
    }

    @Test
    void testIdempotencyOnTransactions() {
        String accountId = "acct-idem-txn-001";

        TransactionRequest request = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-idem-txn-001"
        );

        ResponseEntity<?> response1 = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", request, Map.class);

        ResponseEntity<?> response2 = restTemplate.postForEntity(
            "/accounts/" + accountId + "/transactions", request, Map.class);

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.OK, response2.getStatusCode());

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        Object balanceObj = balanceResponse.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(100.0, ((Number) balanceObj).doubleValue());
    }

    @Test
    void testGetAccountDetails() {
        String accountId = "acct-details-001";

        TransactionRequest request = new TransactionRequest(
            "CREDIT", new BigDecimal("250.00"), "USD", "evt-details-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", request, Map.class);

        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/" + accountId, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(accountId, response.getBody().get("accountId"));
        Object balanceObj = response.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(250.0, ((Number) balanceObj).doubleValue());
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
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/acct-nonexistent/balance", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Object balanceObj = response.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(0.0, ((Number) balanceObj).doubleValue());
    }

    @Test
    void testNegativeBalance() {
        String accountId = "acct-negative-001";

        TransactionRequest credit = new TransactionRequest(
            "CREDIT", new BigDecimal("100.00"), "USD", "evt-neg-001"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", credit, Map.class);

        TransactionRequest debit = new TransactionRequest(
            "DEBIT", new BigDecimal("150.00"), "USD", "evt-neg-002"
        );
        restTemplate.postForEntity("/accounts/" + accountId + "/transactions", debit, Map.class);

        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/accounts/" + accountId + "/balance", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Object balanceObj = response.getBody().get("balance");
        assertTrue(balanceObj instanceof Number);
        assertEquals(-50.0, ((Number) balanceObj).doubleValue());
    }
}

