# Event Ledger - Distributed Microservices System

A production-ready distributed event ledger system built with Spring Boot, featuring two independent microservices that process financial transaction events reliably with full tracing and resilience.

## Quick Start

### Docker (Recommended)
```bash
cd Event-Ledger
docker-compose up --build
```

Services available at:
- Event Gateway API: http://localhost:8080
- Account Service: http://localhost:8081

### Local Development
```bash
cd Event-Ledger
mvn clean package -DskipTests

# Terminal 1: Start Account Service
cd account-service && java -jar target/account-service-1.0.0.jar

# Terminal 2: Start Event Gateway
cd event-gateway-api && java -jar target/event-gateway-api-1.0.0.jar
```

## Key Features

✅ **Idempotency** - Duplicate submissions handled gracefully
✅ **Out-of-Order Tolerance** - Events processed correctly regardless of arrival order  
✅ **Distributed Tracing** - Trace IDs propagated across services via X-Trace-ID header
✅ **Circuit Breaker** - Resilience pattern prevents cascading failures
✅ **Graceful Degradation** - System continues when Account Service is down
✅ **Structured Logging** - JSON logs with full context
✅ **Health Checks** - Service diagnostics on both services
✅ **Custom Metrics** - Event processing metrics tracked
✅ **Docker Compose** - Easy deployment setup

## API Examples

### Submit Event
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {"source": "batch-system"}
  }'
```

### Get Events for Account (ordered by timestamp)
```bash
curl http://localhost:8080/events?account=acct-123
```

### Check Health
```bash
curl http://localhost:8080/events/health
curl http://localhost:8081/accounts/health
```

## Testing

```bash
mvn test
```

Tests cover idempotency, out-of-order events, balance computation, validation, circuit breaker behavior, and trace propagation.

## Resiliency Pattern: Circuit Breaker

The system implements **circuit breaker** to handle Account Service failures:

- **Closed**: Normal operation - requests flow through
- **Open**: After 50% failure rate on 10 calls - requests return 503 immediately  
- **Half-Open**: After 30s wait - tests recovery with up to 3 test calls

Benefits: Prevents cascading failures, reduces load on failing service, provides graceful degradation.

## Project Structure

```
Event-Ledger/
├── common/                    # Shared utilities
│   ├── tracing/              # Trace context manager
│   └── logging/              # Structured logging
├── event-gateway-api/         # Event Gateway (Port 8080)
│   ├── controller/           # REST endpoints
│   ├── service/              # Business logic
│   ├── client/               # Account Service client + resilience
│   ├── repository/           # Data access (H2)
│   └── config/               # Resilience configuration
├── account-service/           # Account Service (Port 8081)
│   ├── controller/           # REST endpoints
│   ├── service/              # Business logic
│   └── repository/           # Data access (H2)
└── docker-compose.yml
```

## Technologies

- Spring Boot 3.2.0
- Resilience4j 2.1.0 (Circuit Breaker, Retry, Timeout)
- H2 Database (in-memory, independent per service)
- OpenTelemetry (tracing)
- Micrometer (metrics)
- Maven
- Docker Compose
- Java 17+
