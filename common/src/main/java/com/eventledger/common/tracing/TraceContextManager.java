package com.eventledger.common.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

@Component
public class TraceContextManager {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    // Avoid calling GlobalOpenTelemetry at class initialization time to prevent
    // ExceptionInInitializerError in environments where OpenTelemetry classes
    // or configuration may not be available at static init. Initialize lazily.
    private static volatile Tracer tracer;

    public static String getTraceIdHeader() {
        return TRACE_ID_HEADER;
    }

    public static String getTraceIdMdcKey() {
        return TRACE_ID_MDC_KEY;
    }

    public static Tracer getTracer() {
        // lazy-init tracer to avoid potential static initializer failures
        if (tracer == null) {
            synchronized (TraceContextManager.class) {
                if (tracer == null) {
                    try {
                        tracer = GlobalOpenTelemetry.getTracer("com.eventledger");
                    } catch (Throwable t) {
                        // If tracer cannot be created (missing classes/config),
                        // create a defensive no-op tracer via a dynamic proxy so
                        // the application won't fail with ExceptionInInitializerError.
                        try {
                            tracer = (Tracer) java.lang.reflect.Proxy.newProxyInstance(
                                Tracer.class.getClassLoader(),
                                new Class[]{Tracer.class},
                                (proxy, method, args) -> null
                            );
                        } catch (Throwable ignored) {
                            // As a last resort, leave tracer null. Callers should
                            // defensively handle a null tracer.
                            tracer = null;
                        }
                    }
                }
            }
        }
        return tracer;
    }

    public static String getCurrentTraceId() {
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().getTraceId();
        if (traceId == null || traceId.equals("0000000000000000")) {
            traceId = java.util.UUID.randomUUID().toString();
        }
        return traceId;
    }
}

