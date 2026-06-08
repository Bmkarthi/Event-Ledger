package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for EventController
 */
@SpringBootTest
@AutoConfigureMockMvc
public class EventControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
    }

    @Test
    void testPostEventSuccess() throws Exception {
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

        EventResponse response = new EventResponse(
            "evt-001",
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            "PROCESSED",
            Instant.now().toString()
        );

        when(eventService.submitEvent(any(EventRequest.class), anyString())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventId").value("evt-001"))
            .andExpect(jsonPath("$.accountId").value("acct-123"))
            .andExpect(jsonPath("$.type").value("CREDIT"));
    }

    @Test
    void testPostEventValidationError() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
            null,  // Missing eventId
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            new HashMap<>()
        );

        when(eventService.submitEvent(any(EventRequest.class), anyString()))
            .thenThrow(new IllegalArgumentException("eventId is required"));

        // Act & Assert
        mockMvc.perform(post("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void testGetEventSuccess() throws Exception {
        // Arrange
        EventResponse response = new EventResponse(
            "evt-001",
            "acct-123",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toString(),
            "PROCESSED",
            Instant.now().toString()
        );

        when(eventService.getEvent("evt-001", anyString())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/events/evt-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt-001"))
            .andExpect(jsonPath("$.type").value("CREDIT"));
    }

    @Test
    void testGetEventNotFound() throws Exception {
        // Arrange
        when(eventService.getEvent("evt-999", anyString()))
            .thenThrow(new EventService.EventNotFoundException("Event not found"));

        // Act & Assert
        mockMvc.perform(get("/events/evt-999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void testGetEventsByAccount() throws Exception {
        // Arrange
        List<EventResponse> events = Arrays.asList(
            new EventResponse("evt-001", "acct-123", "CREDIT", new BigDecimal("100.00"), "USD",
                Instant.now().toString(), "PROCESSED", Instant.now().toString()),
            new EventResponse("evt-002", "acct-123", "DEBIT", new BigDecimal("50.00"), "USD",
                Instant.now().toString(), "PROCESSED", Instant.now().toString())
        );

        when(eventService.getEventsByAccount("acct-123", anyString())).thenReturn(events);

        // Act & Assert
        mockMvc.perform(get("/events?account=acct-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].eventId").value("evt-001"))
            .andExpect(jsonPath("$[1].eventId").value("evt-002"));
    }

    @Test
    void testHealthCheck() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/events/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("event-gateway-api"));
    }
}

