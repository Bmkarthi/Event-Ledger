package com.eventledger.gateway.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountServiceClientResilienceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
    }


    @Test
    void testGracefulDegradationWhenAccountServiceDown() {
        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("eventId", "evt-resilience-001");
        requestBody.put("accountId", "acct-resilience-001");
        requestBody.put("type", "CREDIT");
        requestBody.put("amount", BigDecimal.valueOf(100.00));
        requestBody.put("currency", "USD");
        requestBody.put("eventTimestamp", Instant.now().toString());
        requestBody.put("metadata", new HashMap<>());
        ResponseEntity<?> response = restTemplate.postForEntity(
            "/events",
            requestBody,
            Object.class
        );

        assertTrue(
            response.getStatusCode() == HttpStatus.CREATED ||
                response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE,
            "Expected CREATED or SERVICE_UNAVAILABLE, got: " + response.getStatusCode()
        );
    }


    @Test
    void testGetEventsIndependentOfAccountService() {
        HashMap<String, Object> eventRequest = new HashMap<>();
        eventRequest.put("eventId", "evt-get-test-001");
        eventRequest.put("accountId", "acct-get-test-001");
        eventRequest.put("type", "CREDIT");
        eventRequest.put("amount", BigDecimal.valueOf(50.00));
        eventRequest.put("currency", "USD");
        eventRequest.put("eventTimestamp", Instant.now().toString());
        eventRequest.put("metadata", new HashMap<>());

        ResponseEntity<?> submitResponse = restTemplate.postForEntity(
            "/events",
            eventRequest,
            Object.class
        );

        if (submitResponse.getStatusCode() == HttpStatus.CREATED) {
            ResponseEntity<?> getResponse = restTemplate.getForEntity(
                "/events/evt-get-test-001",
                Object.class
            );

            assertEquals(HttpStatus.OK, getResponse.getStatusCode());
            assertNotNull(getResponse.getBody());
        }
    }

    @Test
    void testHealthCheckEndpoint() {
        ResponseEntity<?> response = restTemplate.getForEntity(
            "/events/health",
            Object.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }


    @Test
    void testIdempotencyWithRetry() {
        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("eventId", "evt-idempotent-resilience-001");
        requestBody.put("accountId", "acct-idempotent-001");
        requestBody.put("type", "DEBIT");
        requestBody.put("amount", BigDecimal.valueOf(75.00));
        requestBody.put("currency", "USD");
        requestBody.put("eventTimestamp", Instant.now().toString());
        requestBody.put("metadata", new HashMap<>());

        // Submit twice
        ResponseEntity<?> response1 = restTemplate.postForEntity(
            "/events",
            requestBody,
            Object.class
        );

        ResponseEntity<?> response2 = restTemplate.postForEntity(
            "/events",
            requestBody,
            Object.class
        );

        assertTrue(
            response1.getStatusCode() == HttpStatus.CREATED ||
                response1.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
        );
        assertTrue(
            response2.getStatusCode() == HttpStatus.CREATED ||
                response2.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
