package com.eventledger.gateway.service;

import com.eventledger.common.logging.StructuredLogger;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final String SERVICE_NAME = "event-gateway-api";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;

    public EventService(EventRepository eventRepository, AccountServiceClient accountServiceClient,
                       MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public EventResponse submitEvent(EventRequest request, String traceId) {
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("eventId", request.getEventId());
        logContext.put("accountId", request.getAccountId());

        try {
            validateEventRequest(request);

            Optional<Event> existingEvent = eventRepository.findByEventId(request.getEventId());
            if (existingEvent.isPresent()) {
                StructuredLogger.logInfo(SERVICE_NAME, "Duplicate event submitted (idempotent)", traceId, logContext);
                meterRegistry.counter("events.duplicate").increment();
                return toResponse(existingEvent.get());
            }

            Instant eventTimestamp = Instant.parse(request.getEventTimestamp());


            String metadataJson;
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {

                logContext.put("error", e.getMessage());
                StructuredLogger.logError(SERVICE_NAME, "Failed to serialize metadata", traceId, logContext);
                throw new EventProcessingException("Failed to serialize metadata", e);
            }

            Event event = new Event(
                request.getEventId(),
                request.getAccountId(),
                Event.EventType.valueOf(request.getType()),
                request.getAmount(),
                request.getCurrency(),
                eventTimestamp,
                metadataJson
            );

            event.setStatus(Event.EventStatus.PENDING);
            Event savedEvent = eventRepository.save(event);

            logContext.put("status", "PENDING");
            StructuredLogger.logInfo(SERVICE_NAME, "Event created", traceId, logContext);
            meterRegistry.counter("events.submitted").increment();


            try {
                accountServiceClient.applyTransaction(
                    request.getAccountId(),
                    request.getType(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getEventId(),
                    traceId
                ).get();

                savedEvent.setStatus(Event.EventStatus.PROCESSED);
                eventRepository.save(savedEvent);
                logContext.put("status", "PROCESSED");
                StructuredLogger.logInfo(SERVICE_NAME, "Event processed successfully", traceId, logContext);
                meterRegistry.counter("events.processed").increment();
            } catch (java.util.concurrent.ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof AccountServiceClient.AccountServiceUnavailableException) {
                    logContext.put("status", "PENDING");
                    logContext.put("reason", "Account Service unavailable");
                    StructuredLogger.logError(SERVICE_NAME, "Event processing deferred - Account Service unavailable", traceId, logContext);
                    meterRegistry.counter("events.processing_failed").increment();
                } else {
                    savedEvent.setStatus(Event.EventStatus.FAILED);
                    eventRepository.save(savedEvent);
                    logContext.put("status", "FAILED");
                    logContext.put("error", ee.getMessage());
                    StructuredLogger.logError(SERVICE_NAME, "Event processing failed", traceId, logContext);
                    meterRegistry.counter("events.processing_failed").increment();
                    throw new EventProcessingException("Failed to process event", ee);
                }
            } catch (AccountServiceClient.AccountServiceUnavailableException e) {
                logContext.put("status", "PENDING");
                logContext.put("reason", "Account Service unavailable");
                StructuredLogger.logError(SERVICE_NAME, "Event processing deferred - Account Service unavailable", traceId, logContext);
                meterRegistry.counter("events.processing_failed").increment();

            } catch (Exception e) {
                savedEvent.setStatus(Event.EventStatus.FAILED);
                eventRepository.save(savedEvent);
                logContext.put("status", "FAILED");
                logContext.put("error", e.getMessage());
                StructuredLogger.logError(SERVICE_NAME, "Event processing failed", traceId, logContext);
                meterRegistry.counter("events.processing_failed").increment();
                throw new EventProcessingException("Failed to process event", e);
            }

            return toResponse(savedEvent);
        } catch (IllegalArgumentException e) {
            StructuredLogger.logError(SERVICE_NAME, "Validation failed: " + e.getMessage(), traceId, logContext);
            meterRegistry.counter("events.validation_failed").increment();
            throw e;
        }
    }


    public EventResponse getEvent(String eventId, String traceId) {
        Optional<Event> event = eventRepository.findByEventId(eventId);
        if (event.isEmpty()) {
            StructuredLogger.logInfo(SERVICE_NAME, "Event not found: " + eventId, traceId,
                Map.of("eventId", eventId));
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        return toResponse(event.get());
    }


    public List<EventResponse> getEventsByAccount(String accountId, String traceId) {
        List<Event> events = eventRepository.findByAccountIdOrderByTimestamp(accountId);
        StructuredLogger.logInfo(SERVICE_NAME, "Retrieved events for account", traceId,
            Map.of("accountId", accountId, "count", events.size()));
        return events.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private void validateEventRequest(EventRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.getEventId() == null || request.getEventId().trim().isEmpty()) {
            errors.add("eventId is required");
        }
        if (request.getAccountId() == null || request.getAccountId().trim().isEmpty()) {
            errors.add("accountId is required");
        }
        if (request.getType() == null || request.getType().trim().isEmpty()) {
            errors.add("type is required");
        } else if (!request.getType().equals("CREDIT") && !request.getType().equals("DEBIT")) {
            errors.add("type must be CREDIT or DEBIT");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("amount must be greater than 0");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("currency is required");
        }
        if (request.getEventTimestamp() == null || request.getEventTimestamp().trim().isEmpty()) {
            errors.add("eventTimestamp is required");
        } else {
            try {
                Instant.parse(request.getEventTimestamp());
            } catch (Exception e) {
                errors.add("eventTimestamp must be valid ISO 8601 format");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation errors: " + String.join(", ", errors));
        }
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
            event.getEventId(),
            event.getAccountId(),
            event.getType().toString(),
            event.getAmount(),
            event.getCurrency(),
            event.getEventTimestamp().toString(),
            event.getStatus().toString(),
            event.getCreatedAt().toString()
        );
    }

    public static class EventProcessingException extends RuntimeException {
        public EventProcessingException(String message) {
            super(message);
        }

        public EventProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class EventNotFoundException extends RuntimeException {
        public EventNotFoundException(String message) {
            super(message);
        }
    }
}

