package com.eventledger.gateway.integration;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete Event Gateway flow
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EventGatewayIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testIdempotency() {
        // Create event request
        EventRequest request = new EventRequest(
            "evt-idempotent-001",
            "acct-idem-001",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        // Submit first time
        ResponseEntity<EventResponse> response1 = restTemplate.postForEntity(
            "/events", request, EventResponse.class);

        // Submit second time (should be idempotent)
        ResponseEntity<EventResponse> response2 = restTemplate.postForEntity(
            "/events", request, EventResponse.class);

        // Assert both return same event
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertNotNull(response1.getBody());
        assertNotNull(response2.getBody());
        assertEquals(response1.getBody().getEventId(), response2.getBody().getEventId());
    }

    @Test
    void testOutOfOrderEventOrdering() {
        String accountId = "acct-order-001";
        Instant baseTime = Instant.now();

        // Create events with different timestamps
        List<EventRequest> events = new ArrayList<>();
        events.add(new EventRequest(
            "evt-order-003",
            accountId,
            "DEBIT",
            new BigDecimal("50.00"),
            "USD",
            baseTime.plusSeconds(30).toString(),  // Arrives third
            new HashMap<>()
        ));

        events.add(new EventRequest(
            "evt-order-001",
            accountId,
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            baseTime.toString(),  // Arrives first
            new HashMap<>()
        ));

        events.add(new EventRequest(
            "evt-order-002",
            accountId,
            "CREDIT",
            new BigDecimal("75.00"),
            "USD",
            baseTime.plusSeconds(15).toString(),  // Arrives second
            new HashMap<>()
        ));

        // Submit out of order
        for (EventRequest event : events) {
            ResponseEntity<EventResponse> response = restTemplate.postForEntity(
                "/events", event, EventResponse.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }

        // Retrieve and verify chronological order
        ResponseEntity<EventResponse[]> listResponse = restTemplate.getForEntity(
            "/events?account=" + accountId, EventResponse[].class);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertEquals(3, listResponse.getBody().length);

        // Verify order is by eventTimestamp
        assertEquals("evt-order-001", listResponse.getBody()[0].getEventId());
        assertEquals("evt-order-002", listResponse.getBody()[1].getEventId());
        assertEquals("evt-order-003", listResponse.getBody()[2].getEventId());
    }

    @Test
    void testValidationErrors() {
        // Missing eventId
        EventRequest invalidRequest = new EventRequest(
            null,
            "acct-test",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/events", invalidRequest, Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testTraceIdPropagation() {
        EventRequest request = new EventRequest(
            "evt-trace-001",
            "acct-trace-001",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        // Custom trace ID header
        ResponseEntity<EventResponse> response = restTemplate
            .withBasicAuth("user", "pass")
            .postForEntity("/events", request, EventResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("evt-trace-001", response.getBody().getEventId());
    }

    @Test
    void testHealthCheck() {
        ResponseEntity<?> response = restTemplate.getForEntity("/events/health", Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}

