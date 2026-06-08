package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import com.eventledger.common.tracing.TraceContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API endpoint for event ingestion and retrieval
 */
@RestController
@RequestMapping("/events")
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * POST /events - Submit a transaction event
     */
    @PostMapping
    public ResponseEntity<?> submitEvent(@RequestBody EventRequest request,
                                         @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("POST /events - Received event [traceId={}]", finalTraceId);

        try {
            EventResponse response = eventService.submitEvent(request, finalTraceId);
            logger.info("POST /events - Event processed successfully [traceId={}]", finalTraceId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("POST /events - Validation error [traceId={}]: {}", finalTraceId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (EventService.EventProcessingException e) {
            logger.error("POST /events - Processing error [traceId={}]: {}", finalTraceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Event processing failed",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            logger.error("POST /events - Unexpected error [traceId={}]", finalTraceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    /**
     * GET /events/{id} - Retrieve a single event by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@PathVariable String id,
                                      @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /events/{} - Retrieving event [traceId={}]", id, finalTraceId);

        try {
            EventResponse response = eventService.getEvent(id, finalTraceId);
            return ResponseEntity.ok(response);
        } catch (EventService.EventNotFoundException e) {
            logger.warn("GET /events/{} - Event not found [traceId={}]", id, finalTraceId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Not found",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        } catch (Exception e) {
            logger.error("GET /events/{} - Error [traceId={}]", id, finalTraceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "traceId", finalTraceId
            ));
        }
    }

    /**
     * GET /events?account={accountId} - List events for an account
     */
    @GetMapping
    public ResponseEntity<?> getEventsByAccount(@RequestParam String account,
                                                @RequestHeader(value = "X-Trace-ID", required = false) String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        logger.info("GET /events?account={} - Retrieving events [traceId={}]", account, finalTraceId);

        try {
            List<EventResponse> events = eventService.getEventsByAccount(account, finalTraceId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            logger.error("GET /events?account={} - Error [traceId={}]", account, finalTraceId, e);
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
            "service", "event-gateway-api",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

