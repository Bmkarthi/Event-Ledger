package com.eventledger.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class StructuredLogger {

    private static class ObjectMapperHolder {
        static final ObjectMapper INSTANCE = new ObjectMapper();
    }

    private static ObjectMapper getObjectMapper() {
        return ObjectMapperHolder.INSTANCE;
    }

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
            System.out.println(getObjectMapper().writeValueAsString(logEntry));
        } catch (Exception e) {
            System.out.println("{\"timestamp\":\"" + Instant.now() + "\",\"level\":\"ERROR\",\"message\":\"Failed to serialize log\"}");
        }

        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
    }

    public static void logInfo(String serviceName, String message, String traceId, Map<String, Object> context) {
        logStructured(serviceName, "INFO", message, traceId, context);
    }

    public static void logError(String serviceName, String message, String traceId, Map<String, Object> context) {
        logStructured(serviceName, "ERROR", message, traceId, context);
    }

    public static void clearContext() {
        MDC.clear();
    }
}

