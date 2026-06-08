package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.Event;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.gateway.client.AccountServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService
 */
public class EventServiceTest {

    private EventService eventService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    private MeterRegistry meterRegistry;
    private String traceId = "trace-123";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        eventService = new EventService(eventRepository, accountServiceClient, meterRegistry);
    }

    @Test
    void testSubmitEventSuccess() {
        // Arrange
        EventRequest request = new EventRequest(
            "evt-001",
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        when(accountServiceClient.applyTransaction(anyString(), anyString(), any(BigDecimal.class),
            anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("SUCCESS"));

        // Act
        EventResponse response = eventService.submitEvent(request, traceId);

        // Assert
        assertNotNull(response);
        assertEquals("evt-001", response.getEventId());
        assertEquals("CREDIT", response.getType());
        assertEquals("acct-123", response.getAccountId());
        verify(eventRepository, times(2)).save(any(Event.class));
    }

    @Test
    void testSubmitEventIdempotency() {
        // Arrange
        EventRequest request = new EventRequest(
            "evt-001",
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        Event existingEvent = new Event("evt-001", "acct-123", Event.EventType.CREDIT,
            new BigDecimal("100.00"), "USD", Instant.now(), "{}");
        existingEvent.setId(1L);
        existingEvent.setStatus(Event.EventStatus.PROCESSED);

        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existingEvent));

        // Act
        EventResponse response = eventService.submitEvent(request, traceId);

        // Assert
        assertNotNull(response);
        assertEquals("evt-001", response.getEventId());
        assertEquals("PROCESSED", response.getStatus());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void testSubmitEventValidationFailure() {
        // Arrange - missing eventId
        EventRequest request = new EventRequest(
            null,
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            eventService.submitEvent(request, traceId);
        });
    }

    @Test
    void testSubmitEventInvalidAmount() {
        // Arrange - zero amount
        EventRequest request = new EventRequest(
            "evt-001",
            "acct-123",
            "CREDIT",
            BigDecimal.ZERO,
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            eventService.submitEvent(request, traceId);
        });
    }

    @Test
    void testGetEventNotFound() {
        // Arrange
        when(eventRepository.findByEventId("evt-999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EventService.EventNotFoundException.class, () -> {
            eventService.getEvent("evt-999", traceId);
        });
    }

    @Test
    void testGetEventsByAccountOrderedByTimestamp() {
        // Arrange
        Instant now = Instant.now();
        Event event1 = new Event("evt-001", "acct-123", Event.EventType.CREDIT,
            new BigDecimal("100.00"), "USD", now.minusSeconds(100), "{}");
        Event event2 = new Event("evt-002", "acct-123", Event.EventType.DEBIT,
            new BigDecimal("50.00"), "USD", now, "{}");

        List<Event> events = Arrays.asList(event1, event2);

        when(eventRepository.findByAccountIdOrderByTimestamp("acct-123")).thenReturn(events);

        // Act
        List<EventResponse> responses = eventService.getEventsByAccount("acct-123", traceId);

        // Assert
        assertEquals(2, responses.size());
        assertEquals("evt-001", responses.get(0).getEventId());
        assertEquals("evt-002", responses.get(1).getEventId());
    }
}

