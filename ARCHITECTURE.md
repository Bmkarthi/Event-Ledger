# Event Ledger - Architecture & Design Documentation

## System Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    External Clients                             │
│               (Browsers, API Consumers)                         │
└────────────────────────┬────────────────────────────────────────┘
                         │ HTTP/REST (Public)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              Event Gateway API (Port 8080)                      │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ EventController                                          │  │
│  │ • POST /events       - Submit event                     │  │
│  │ • GET /events/{id}   - Get event by ID                  │  │
│  │ • GET /events?acc=X  - List account events              │  │
│  │ • GET /health        - Health check                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│           ▲                              ▲                       │
│           │ (calls)                      │ (uses)                │
│  ┌────────┴──────────┐          ┌────────┴─────────────┐       │
│  │ EventService      │          │ EventRepository      │       │
│  │                   │          │ (JPA/H2)             │       │
│  │ • Validation      │          │                      │       │
│  │ • Idempotency     │          │ Events Table:        │       │
│  │ • Call Account    │          │ • event_id (PK)      │       │
│  │   Service         │          │ • account_id         │       │
│  │ • Metrics         │          │ • type               │       │
│  └────────┬──────────┘          │ • amount             │       │
│           │ (calls via)          │ • timestamp          │       │
│  ┌────────▼──────────────────────┴─────────────────────┐       │
│  │                                                      │       │
│  │ AccountServiceClient (with Resilience Patterns)    │       │
│  │                                                      │       │
│  │ • Circuit Breaker (50% threshold, 30s wait)        │       │
│  │ • Retry (3 attempts, 500ms backoff)                │       │
│  │ • Time Limiter (10s timeout)                       │       │
│  │ • Trace ID propagation (X-Trace-ID header)        │       │
│  └────────┬──────────────────────────────────────────────┘       │
│           │ HTTP/REST (Internal, Synchronous)                   │
└───────────┼──────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Account Service (Port 8081)                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ AccountController                                        │  │
│  │ • POST /accounts/{id}/transactions - Apply transaction  │  │
│  │ • GET /accounts/{id}/balance - Get balance              │  │
│  │ • GET /accounts/{id} - Get account details              │  │
│  │ • GET /health - Health check                            │  │
│  └──────────────────────────────────────────────────────────┘  │
│           ▲                              ▲                       │
│           │ (calls)                      │ (uses)                │
│  ┌────────┴──────────┐          ┌────────┴─────────────┐       │
│  │ AccountService    │          │ AccountRepository    │       │
│  │                   │          │ TransactionRepository│       │
│  │ • Apply Txn       │          │ (JPA/H2)             │       │
│  │ • Idempotency     │          │                      │       │
│  │ • Balance Calc    │          │ Accounts Table:      │       │
│  │ • Metrics         │          │ • account_id (PK)    │       │
│  └───────────────────┘          │ • balance            │       │
│                                 │ • created_at         │       │
│                                 │                      │       │
│                                 │ Transactions Table:  │       │
│                                 │ • type               │       │
│                                 │ • amount             │       │
│                                 │ • idempotency_key(PK)│       │
│                                 │ • created_at         │       │
│                                 └──────────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### 1. Microservices Architecture
**Choice**: Two independent services
**Rationale**:
- Clear separation of concerns
- Independent scalability
- Fault isolation
- Technology diversity if needed

### 2. Synchronous Communication
**Choice**: REST/HTTP with resilience patterns
**Rationale**:
- Simpler implementation than async messaging
- Immediate feedback for error handling
- Easier testing and debugging
- Appropriate for transactional systems

**Alternative (Async)**: Message queues (Kafka, RabbitMQ)
- Would add complexity
- Better for eventual consistency scenarios
- Would need event sourcing and saga pattern

### 3. Idempotency Implementation
**Choice**: Database unique constraints on event IDs and idempotency keys
**Rationale**:
- Simple and reliable
- No additional cache needed
- Works across restarts
- Guaranteed by database

**How it works**:
```
Event Gateway:
- Event table has unique constraint on event_id
- Duplicate eventId returns existing event

Account Service:
- Transaction table has unique constraint on idempotency_key
- Duplicate idempotency_key skips transaction re-application
```

### 4. Out-of-Order Event Handling
**Choice**: Store by eventTimestamp, query ordered by timestamp
**Rationale**:
- Correct balance regardless of arrival order
- Event listings show chronological order
- Simple implementation using SQL ORDER BY

**Algorithm**:
1. Accept events in any order
2. Store with eventTimestamp
3. Balance = SUM(CREDIT where timestamp < now) - SUM(DEBIT where timestamp < now)
4. Listings ordered by eventTimestamp ASC

### 5. Resilience Pattern Choice: Circuit Breaker
**Pattern Chosen**: Circuit Breaker
**Why not others**:
- Timeout + Retry: Can overwhelm failing service
- Bulkhead: Doesn't stop repeated failures
- Circuit Breaker: Best overall for this use case

**Configuration**:
```yaml
Closed State:
- Requests flow normally
- Sliding window: 10 calls

Open State (triggered when):
- Failure rate > 50% on 10 calls
- Response: 503 Service Unavailable
- Duration: 30 seconds

Half-Open State:
- After 30 seconds, allow 3 test calls
- If successful, close circuit (recovery)
- If fail, reopen circuit
```

**Fallback Behavior**:
```
When Open:
- POST /events: Event saved as PENDING, return 503
- GET /events: Works (reads from local database)
- GET /accounts: Returns error (needs Account Service)
```

### 6. Database Strategy
**Choice**: Separate H2 in-memory databases
**Rationale**:
- True service independence
- No shared state
- Easy to replace with persistent DB

**Event Gateway DB**:
```sql
CREATE TABLE events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(255) UNIQUE NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    type ENUM('CREDIT', 'DEBIT'),
    amount DECIMAL(19,4),
    currency VARCHAR(10),
    event_timestamp TIMESTAMP,
    created_at TIMESTAMP,
    metadata TEXT,
    status ENUM('PENDING', 'PROCESSED', 'FAILED')
);

CREATE INDEX idx_account_timestamp ON events(account_id, event_timestamp);
```

**Account Service DB**:
```sql
CREATE TABLE accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id VARCHAR(255) UNIQUE NOT NULL,
    balance DECIMAL(19,4),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id VARCHAR(255),
    type ENUM('CREDIT', 'DEBIT'),
    amount DECIMAL(19,4),
    currency VARCHAR(10),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP,
    status ENUM('SUCCESS', 'FAILED')
);
```

### 7. Tracing Strategy
**Choice**: Trace ID propagation via HTTP headers
**Implementation**:
1. Gateway generates/extracts X-Trace-ID header
2. Gateway propagates X-Trace-ID to Account Service
3. Both services log with trace ID
4. Full request path traceable

**Example Flow**:
```
Client Request:
  curl -H "X-Trace-ID: abc-123" POST /events
  
Gateway Logs:
  [traceId=abc-123] Received event evt-001
  [traceId=abc-123] Calling Account Service
  
Account Service Logs:
  [traceId=abc-123] Processing transaction for acct-123
  [traceId=abc-123] Balance updated

Gateway Logs:
  [traceId=abc-123] Event processed successfully
```

## Data Flow Scenarios

### Scenario 1: Happy Path - Event Submission
```
1. Client submits event to POST /events
2. Gateway generates trace ID (or uses X-Trace-ID header)
3. Gateway validates event structure
4. Gateway checks idempotency (eventId not in DB)
5. Gateway creates Event record with status=PENDING
6. Gateway calls Account Service with trace ID
7. Account Service creates/updates account
8. Account Service creates transaction record
9. Gateway updates Event status=PROCESSED
10. Gateway returns 201 Created with EventResponse
```

**Success Response**: 201 Created
**Error Responses**: 
- 400 Bad Request (validation failure)
- 409 Conflict (idempotency - duplicate returns existing event)
- 503 Service Unavailable (Account Service down, event still created)

### Scenario 2: Out-of-Order Events
```
Event 1: timestamp=10:00, arrives at 10:05
Event 2: timestamp=10:15, arrives at 10:00
Event 3: timestamp=10:10, arrives at 10:03

Storage (in arrival order):
1. event_timestamp=10:15, status=PROCESSED
2. event_timestamp=10:00, status=PROCESSED
3. event_timestamp=10:10, status=PROCESSED

Query GET /events?account=X returns:
1. timestamp=10:00 (CREDIT 100) balance=100
2. timestamp=10:10 (DEBIT 50) balance=50
3. timestamp=10:15 (CREDIT 25) balance=75

Correct balance: 75 ✓
```

### Scenario 3: Idempotent Re-submission
```
First Request:
  eventId=evt-001
  → Event created, status=PENDING
  → Account Service called
  → Status updated to PROCESSED
  → Returns 201 Created

Second Request (same eventId):
  eventId=evt-001
  → Database has evt-001
  → Returns 200 OK with existing event
  → Account Service NOT called
  → Balance NOT changed again ✓
```

### Scenario 4: Circuit Breaker Opens (Account Service Down)
```
1. Account Service becomes unhealthy
2. Multiple calls fail (>50% failure rate)
3. Circuit breaker transitions to OPEN
4. Subsequent calls to Account Service:
   - Immediately return 503 (no attempt to call)
   - Events still created with status=PENDING
   - GET /events still works (local data)

5. After 30 seconds:
   - Circuit breaker transitions to HALF_OPEN
   - 3 test calls allowed to Account Service
   - If successful: CLOSED (recovery)
   - If failed: OPEN (still down)
```

## Technology Stack Rationale

| Component | Technology | Why |
|-----------|-----------|-----|
| Framework | Spring Boot 3.2 | Enterprise standard, excellent ecosystem |
| Resilience | Resilience4j 2.1 | Lightweight, no external service needed |
| Database | H2 | In-memory, no setup required, easily replaceable |
| ORM | Spring Data JPA | Declarative, less boilerplate |
| Tracing | OpenTelemetry | Standard, vendor-agnostic |
| Metrics | Micrometer | Standard, integrates with Prometheus/Grafana |
| Testing | JUnit 5 + Mockito | Standard, comprehensive |
| Build | Maven 3.8+ | Industry standard, declarative |
| Containers | Docker | Standard for modern deployments |

## Performance Characteristics

### Expected Throughput
- **Event Gateway**: ~1000 events/sec (with single instance)
- **Account Service**: ~500 transactions/sec (limited by DB)
- **Network RTT**: ~10ms between services (Docker/same host)

### Scalability Considerations
- **Horizontal**: Add load balancer + multiple instances
- **Vertical**: Increase JVM heap, tune GC
- **Database**: Replace H2 with PostgreSQL, add connection pooling
- **Caching**: Add Redis for frequently accessed accounts

### Bottlenecks
1. **Account Service Database**: Single H2 instance
2. **Network Latency**: Event Gateway → Account Service RTT
3. **Circuit Breaker Recovery**: 30-second wait in open state

## Security Considerations

### Not Implemented (Out of Scope)
- Authentication/Authorization
- Rate limiting
- Input sanitization
- SSL/TLS encryption
- CORS policies

### For Production Add
- OAuth2 / JWT authentication
- Rate limiting (e.g., Resilience4j RateLimiter)
- Input validation library (e.g., Hibernate Validator)
- SSL/TLS certificates
- CORS configuration
- API versioning strategy
- Request signing

## Monitoring & Observability

### Implemented
- Health checks (`/health` endpoints)
- Custom metrics (event counters)
- Structured logging with trace IDs
- Request/response logging

### For Production Add
- Centralized logging (ELK Stack, Splunk)
- Distributed tracing (Jaeger, Zipkin)
- Metrics collection (Prometheus)
- Visualization (Grafana)
- Alerting (PagerDuty, Opsgenie)
- APM (DataDog, New Relic)

## Testing Strategy

### Unit Tests
- Service logic (EventService, AccountService)
- Validation logic
- Business calculations

### Integration Tests
- Controller → Service → Repository flow
- Idempotency checks
- Out-of-order event handling
- Resilience patterns

### End-to-End Tests (Manual)
- Docker Compose startup
- API testing with curl/Postman
- Circuit breaker behavior
- Performance testing

## Deployment Architecture

### Development
```
Local Machine:
├── Account Service (localhost:8081)
└── Event Gateway (localhost:8080)
```

### Docker Compose
```
Docker Network:
├── Account Service (account-service:8081)
├── Event Gateway (event-gateway-api:8080)
└── Optional: Monitoring stack
```

### Production (Kubernetes)
```
Namespace: event-ledger
├── Deployment: account-service (replicas: 3)
├── Deployment: event-gateway-api (replicas: 3)
├── Service: account-service (ClusterIP)
├── Service: event-gateway-api (LoadBalancer)
├── ConfigMap: application configuration
├── Secret: database credentials
├── PostgreSQL StatefulSet
├── Redis Cache
└── Monitoring: Prometheus, Grafana
```

## Future Enhancements

### Short Term
- Add Prometheus metrics endpoint
- Implement exponential backoff with jitter
- Add contract tests (Pact)
- Add API documentation (Swagger/OpenAPI)

### Medium Term
- Event sourcing for full audit trail
- Async processing with message queues
- Read replicas for Account Service
- GraphQL interface
- WebSocket support for real-time updates

### Long Term
- Multi-region deployment
- CQRS pattern for reads/writes
- Event replay capability
- Machine learning for fraud detection
- Blockchain integration for immutability

## Conclusion

This architecture provides:
- **Reliability**: Circuit breaker prevents cascading failures
- **Correctness**: Idempotency and out-of-order handling
- **Observability**: Trace IDs and structured logging
- **Scalability**: Independent services, easy horizontal scaling
- **Simplicity**: Synchronous communication, minimal dependencies
- **Testability**: Comprehensive unit and integration tests

The system successfully handles the core challenges of distributed event processing while maintaining simplicity and operational efficiency.

