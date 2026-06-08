package com.eventledger.common.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

/**
 * Manages trace context and trace ID propagation across services.
 */
@Component
public class TraceContextManager {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("com.eventledger");

    public static String getTraceIdHeader() {
        return TRACE_ID_HEADER;
    }

    public static String getTraceIdMdcKey() {
        return TRACE_ID_MDC_KEY;
    }

    public static Tracer getTracer() {
        return tracer;
    }

    /**
     * Get current span's trace ID or generate a new one
     */
    public static String getCurrentTraceId() {
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().getTraceId();
        if (traceId == null || traceId.equals("0000000000000000")) {
            traceId = java.util.UUID.randomUUID().toString();
        }
        return traceId;
    }
}

