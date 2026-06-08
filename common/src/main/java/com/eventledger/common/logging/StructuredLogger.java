package com.eventledger.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Structured logging utility for JSON-formatted logs with trace context.
 */
@Component
public class StructuredLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Log a structured message with context
     */
    public static void logStructured(String serviceName, String level, String message, String traceId, Map<String, Object> context) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("service", serviceName);
        logEntry.put("level", level);
        logEntry.put("message", message);
        logEntry.put("traceId", traceId);

        if (context != null) {
            logEntry.putAll(context);
        }

        try {
            System.out.println(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            System.out.println("{\"timestamp\":\"" + Instant.now() + "\",\"level\":\"ERROR\",\"message\":\"Failed to serialize log\"}");
        }

        // Also set MDC for traditional logging
        MDC.put("traceId", traceId);
    }

    /**
     * Convenience method for info level
     */
    public static void logInfo(String serviceName, String message, String traceId, Map<String, Object> context) {
        logStructured(serviceName, "INFO", message, traceId, context);
    }

    /**
     * Convenience method for error level
     */
    public static void logError(String serviceName, String message, String traceId, Map<String, Object> context) {
        logStructured(serviceName, "ERROR", message, traceId, context);
    }

    /**
     * Clear MDC
     */
    public static void clearContext() {
        MDC.clear();
    }
}

