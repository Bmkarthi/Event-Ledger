package com.eventledger.gateway.controller;

import com.eventledger.common.logging.StructuredLogger;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventController {

    private static final String SERVICE_NAME = "event-gateway-api";

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<?> submitEvent(@RequestBody EventRequest request,
                                         @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;

        Map<String, Object> logContext = new HashMap<>();
        if (request != null) {
            if (request.getEventId() != null) {
                logContext.put("eventId", request.getEventId());
            }
            if (request.getAccountId() != null) {
                logContext.put("accountId", request.getAccountId());
            }
        }

        StructuredLogger.logInfo(SERVICE_NAME, "POST /events - Received event", finalTraceId, logContext);

        try {
            EventResponse response = eventService.submitEvent(request, finalTraceId);
            StructuredLogger.logInfo(SERVICE_NAME, "Event processed successfully", finalTraceId,
                Map.of("eventId", request.getEventId(), "status", response.getStatus()));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            StructuredLogger.logError(SERVICE_NAME, "Validation failed: " + e.getMessage(), finalTraceId,
                Map.of("eventId", request.getEventId() != null ? request.getEventId() : "unknown"));
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (EventService.EventProcessingException e) {
            StructuredLogger.logError(SERVICE_NAME, "Event processing failed: " + e.getMessage(), finalTraceId,
                Map.of("eventId", request.getEventId() != null ? request.getEventId() : "unknown"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Event processing failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (AccountServiceClient.AccountServiceUnavailableException e) {
            StructuredLogger.logError(SERVICE_NAME, "Account Service unavailable", finalTraceId,
                Map.of("eventId", request.getEventId() != null ? request.getEventId() : "unknown", "reason", "Account Service unavailable"));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service unavailable",
                "message", "Account Service is currently unavailable. Event was stored but not processed.",
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Unexpected error: " + e.getMessage(), finalTraceId,
                Map.of("eventId", request.getEventId() != null ? request.getEventId() : "unknown"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@PathVariable("id") String id,
                                      @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        StructuredLogger.logInfo(SERVICE_NAME, "GET /events - Retrieving event", finalTraceId,
            Map.of("eventId", id));

        try {
            EventResponse response = eventService.getEvent(id, finalTraceId);
            return ResponseEntity.ok(response);
        } catch (EventService.EventNotFoundException e) {
            StructuredLogger.logInfo(SERVICE_NAME, "Event not found", finalTraceId, Map.of("eventId", id));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Not found",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving event: " + e.getMessage(), finalTraceId,
                Map.of("eventId", id));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping
    public ResponseEntity<?> getEventsByAccount(@RequestParam("account") String account,
                                                @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        StructuredLogger.logInfo(SERVICE_NAME, "GET /events - Retrieving events", finalTraceId,
            Map.of("accountId", account));

        try {
            List<EventResponse> events = eventService.getEventsByAccount(account, finalTraceId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            StructuredLogger.logError(SERVICE_NAME, "Error retrieving events: " + e.getMessage(), finalTraceId,
                Map.of("accountId", account));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "event-gateway-api",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

