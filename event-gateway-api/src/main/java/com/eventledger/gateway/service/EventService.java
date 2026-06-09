package com.eventledger.gateway.service;

import com.eventledger.common.logging.StructuredLogger;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.model.Event;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
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
        logger.info("Submitting event: {}", request.getEventId());

        Map<String, Object> logContext = new HashMap<>();
        logContext.put("eventId", request.getEventId());
        logContext.put("accountId", request.getAccountId());

        try {
            validateEventRequest(request);
            logger.info("Event validation passed for event: {}", request.getEventId());

            Optional<Event> existingEvent = eventRepository.findByEventId(request.getEventId());
            if (existingEvent.isPresent()) {
                StructuredLogger.logInfo(SERVICE_NAME, "Duplicate event submitted (idempotent)", traceId, logContext);
                meterRegistry.counter("events.duplicate").increment();
                logger.info("Duplicate event detected: {}", request.getEventId());
                return toResponse(existingEvent.get());
            }

            Instant eventTimestamp = Instant.parse(request.getEventTimestamp());

            String metadataJson;
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logContext.put("error", e.getMessage());
                StructuredLogger.logError(SERVICE_NAME, "Failed to serialize metadata", traceId, logContext);
                logger.error("Failed to serialize metadata for event: {}", request.getEventId(), e);
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
            logger.info("Event created with PENDING status: {}", request.getEventId());

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
                logger.info("Event processed successfully: {}", request.getEventId());
            } catch (java.util.concurrent.ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                if (cause instanceof AccountServiceClient.AccountServiceUnavailableException) {
                    logContext.put("status", "PENDING");
                    logContext.put("reason", "Account Service unavailable");
                    StructuredLogger.logError(SERVICE_NAME, "Event processing deferred - Account Service unavailable", traceId, logContext);
                    meterRegistry.counter("events.processing_deferred").increment();
                    logger.warn("Event processing deferred due to Account Service unavailability: {}", request.getEventId());
                } else {
                    savedEvent.setStatus(Event.EventStatus.FAILED);
                    eventRepository.save(savedEvent);
                    logContext.put("status", "FAILED");
                    logContext.put("error", executionException.getMessage());
                    StructuredLogger.logError(SERVICE_NAME, "Event processing failed", traceId, logContext);
                    meterRegistry.counter("events.processing_failed").increment();
                    logger.error("Event processing failed: {}", request.getEventId(), executionException);
                    throw new EventProcessingException("Failed to process event", executionException);
                }
            } catch (AccountServiceClient.AccountServiceUnavailableException unavailableException) {
                logContext.put("status", "PENDING");
                logContext.put("reason", "Account Service unavailable");
                StructuredLogger.logError(SERVICE_NAME, "Event processing deferred - Account Service unavailable", traceId, logContext);
                meterRegistry.counter("events.processing_deferred").increment();
                logger.warn("Account Service unavailable for event: {}", request.getEventId());

            } catch (Exception e) {
                savedEvent.setStatus(Event.EventStatus.FAILED);
                eventRepository.save(savedEvent);
                logContext.put("status", "FAILED");
                logContext.put("error", e.getMessage());
                StructuredLogger.logError(SERVICE_NAME, "Event processing failed", traceId, logContext);
                meterRegistry.counter("events.processing_failed").increment();
                logger.error("Unexpected error processing event: {}", request.getEventId(), e);
                throw new EventProcessingException("Failed to process event", e);
            }

            return toResponse(savedEvent);
        } catch (IllegalArgumentException validationException) {
            StructuredLogger.logError(SERVICE_NAME, "Validation failed: " + validationException.getMessage(), traceId, logContext);
            meterRegistry.counter("events.validation_failed").increment();
            logger.warn("Event validation failed: {}", validationException.getMessage());
            throw validationException;
        }
    }
    public EventResponse getEvent(String eventId, String traceId) {
        logger.info("Retrieving event: {}", eventId);

        Optional<Event> event = eventRepository.findByEventId(eventId);
        if (event.isEmpty()) {
            StructuredLogger.logInfo(SERVICE_NAME, "Event not found", traceId, Map.of("eventId", eventId));
            logger.warn("Event not found: {}", eventId);
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        return toResponse(event.get());
    }

    public List<EventResponse> getEventsByAccount(String accountId, String traceId) {
        logger.info("Retrieving events for account: {}", accountId);

        List<Event> events = eventRepository.findByAccountIdOrderByTimestamp(accountId);
        StructuredLogger.logInfo(SERVICE_NAME, "Retrieved events for account", traceId,
            Map.of("accountId", accountId, "count", events.size()));
        logger.info("Events retrieved for account: {}, count: {}", accountId, events.size());
        return events.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private void validateEventRequest(EventRequest request) {
        logger.info("Validating event request: {}", request.getEventId());

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
            logger.warn("Event validation failed: {}", String.join(", ", errors));
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
