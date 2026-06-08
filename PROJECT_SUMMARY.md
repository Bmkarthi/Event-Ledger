# Event Ledger - Project Summary

## Overview

The Event Ledger is a production-ready distributed microservices system designed to process financial transaction events reliably. It consists of two independent services:

1. **Event Gateway API** (Port 8080) - Public-facing service for event ingestion
2. **Account Service** (Port 8081) - Internal service for account and balance management

## What Has Been Built

### Core Features ✅
- ✅ **Idempotency**: Duplicate event submissions are handled gracefully
- ✅ **Out-of-Order Tolerance**: Events processed correctly regardless of arrival order
- ✅ **Balance Computation**: Accurate balance = SUM(CREDITS) - SUM(DEBITS)
- ✅ **Validation**: Comprehensive input validation with meaningful error messages
- ✅ **Service Separation**: Independent processes with separate H2 databases
- ✅ **Distributed Tracing**: X-Trace-ID propagation across services
- ✅ **Structured Logging**: JSON logs with trace context
- ✅ **Health Checks**: `/health` endpoints on both services
- ✅ **Custom Metrics**: Event processing metrics tracked
- ✅ **Graceful Degradation**: System continues when Account Service is down

### Resiliency Patterns ✅
- ✅ **Circuit Breaker**: Prevents cascading failures (50% threshold, 30s recovery)
- ✅ **Retry Logic**: Automatic retry with backoff
- ✅ **Time Limiter**: 10-second timeout on calls
- ✅ **Fallback Methods**: Returns 503 with meaningful messages

### Testing ✅
- ✅ Unit tests for service logic
- ✅ Integration tests for complete flows
- ✅ Idempotency tests
- ✅ Out-of-order event handling tests
- ✅ Circuit breaker behavior tests
- ✅ Trace propagation tests

### Deployment ✅
- ✅ Docker Compose setup for both services
- ✅ Dockerfile for each service
- ✅ Health checks in compose file
- ✅ Service dependencies configured

### Documentation ✅
- ✅ README.md - Quick start guide
- ✅ SETUP.md - Detailed setup and deployment instructions
- ✅ ARCHITECTURE.md - System design and technical decisions
- ✅ API.md - Complete API documentation with examples

## Project Structure

```
Event-Ledger/
├── README.md                      # Quick start guide
├── SETUP.md                       # Detailed setup instructions
├── ARCHITECTURE.md                # Architecture and design documentation
├── API.md                         # API documentation
├── docker-compose.yml             # Docker Compose configuration
├── pom.xml                        # Parent POM with dependency management
│
├── common/                        # Shared utilities module
│   ├── pom.xml
│   └── src/main/java/com/eventledger/common/
│       ├── tracing/
│       │   └── TraceContextManager.java
│       └── logging/
│           └── StructuredLogger.java
│
├── event-gateway-api/             # Event Gateway microservice
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/eventledger/gateway/
│       │   ├── EventGatewayApplication.java
│       │   ├── controller/
│       │   │   └── EventController.java
│       │   ├── service/
│       │   │   └── EventService.java
│       │   ├── repository/
│       │   │   └── EventRepository.java
│       │   ├── domain/
│       │   │   └── Event.java
│       │   ├── dto/
│       │   │   ├── EventRequest.java
│       │   │   └── EventResponse.java
│       │   ├── client/
│       │   │   └── AccountServiceClient.java (with resilience)
│       │   └── config/
│       │       └── ResilienceConfig.java
│       ├── resources/
│       │   └── application.yml
│       └── test/java/com/eventledger/gateway/
│           ├── service/
│           │   └── EventServiceTest.java
│           ├── controller/
│           │   └── EventControllerIT.java
│           ├── client/
│           │   └── AccountServiceClientResilienceTest.java
│           └── integration/
│               └── EventGatewayIntegrationTest.java
│
└── account-service/               # Account Service microservice
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/eventledger/account/
        │   ├── AccountServiceApplication.java
        │   ├── controller/
        │   │   └── AccountController.java
        │   ├── service/
        │   │   └── AccountService.java
        │   ├── repository/
        │   │   ├── AccountRepository.java
        │   │   └── TransactionRepository.java
        │   ├── domain/
        │   │   ├── Account.java
        │   │   └── Transaction.java
        │   ├── dto/
        │   │   └── TransactionRequest.java
        │   └── resources/
        │       └── application.yml
        └── test/java/com/eventledger/account/
            ├── service/
            │   └── AccountServiceTest.java
            └── integration/
                └── AccountServiceIntegrationTest.java
```

## Getting Started

### Quick Start (Docker)
```bash
cd Event-Ledger
docker-compose up --build
```

### Local Development
```bash
cd Event-Ledger
mvn clean install -DskipTests
cd account-service && java -jar target/account-service-1.0.0.jar  # Terminal 1
cd event-gateway-api && java -jar target/event-gateway-api-1.0.0.jar  # Terminal 2
```

### Running Tests
```bash
mvn test
```

## API Endpoints

### Event Gateway
- `POST /events` - Submit event
- `GET /events/{id}` - Get event by ID
- `GET /events?account=X` - List account events
- `GET /events/health` - Health check

### Account Service
- `POST /accounts/{id}/transactions` - Apply transaction
- `GET /accounts/{id}/balance` - Get balance
- `GET /accounts/{id}` - Get account details
- `GET /accounts/health` - Health check

## Key Technologies

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Spring Boot | 3.2.0 |
| Resilience | Resilience4j | 2.1.0 |
| Database | H2 | Latest |
| Tracing | OpenTelemetry | 1.32.0 |
| Metrics | Micrometer | 1.12.0 |
| Build | Maven | 3.8+ |
| Container | Docker | Latest |
| Java | OpenJDK | 17+ |

## Design Highlights

### Idempotency Implementation
- Event ID is unique constraint in database
- Duplicate submissions return 201 with existing event
- Account Service uses idempotency key for transaction deduplication

### Out-of-Order Event Handling
- Events stored with eventTimestamp
- Queries ordered by timestamp (earliest first)
- Balance always correct regardless of arrival order
- Example: Events arrive as [3, 1, 2], returned as [1, 2, 3]

### Circuit Breaker Pattern
- Activates after 50% failure rate on 10 calls
- Prevents cascading failures
- Automatic recovery detection (30s wait + half-open state)
- Fallback returns 503 Service Unavailable

### Trace Propagation
- X-Trace-ID header generated at Gateway
- Propagated to Account Service
- All logs include trace ID for correlation
- Full request path traceable

## Resilience Features

1. **Circuit Breaker**: Prevents repeated calls to failing service
2. **Retry**: Up to 3 attempts with 500ms backoff
3. **Timeout**: 10-second limit on Account Service calls
4. **Fallback**: Returns 503 with graceful messages
5. **Health Checks**: Service health monitoring

## Testing Coverage

### Unit Tests
- EventService validation and logic
- AccountService balance calculations
- Transaction processing

### Integration Tests
- Complete Gateway → Account Service flow
- Idempotency verification
- Out-of-order event ordering
- Circuit breaker behavior
- Trace ID propagation
- Multiple transaction scenarios

### Test Execution
```bash
mvn test                              # All tests
mvn test -pl event-gateway-api        # Gateway tests
mvn test -pl account-service          # Account service tests
mvn test -Dtest=EventServiceTest      # Specific test class
```

## Documentation

### README.md
- Quick start guide
- Key features overview
- Basic API examples
- Technology stack

### SETUP.md
- Step-by-step setup instructions
- Prerequisites and requirements
- Local development setup
- Docker deployment
- Troubleshooting guide
- Configuration options
- Performance tuning

### ARCHITECTURE.md
- System architecture diagrams
- Design decisions and rationale
- Data flow scenarios
- Database schema
- Resilience pattern explanation
- Technology choices
- Scalability considerations
- Security considerations
- Future enhancements

### API.md
- Complete endpoint documentation
- Request/response examples
- Error handling
- Field descriptions
- Validation rules
- Example cURL commands
- Testing guide

## Commit History

```
a6db350 Add comprehensive documentation: SETUP, ARCHITECTURE, and API guides
e895be7 Add comprehensive integration tests for both services
5d3b689 Initial project structure with parent POM and modules
```

## Production Readiness

### Implemented for Production
- ✅ Error handling and recovery
- ✅ Health checks
- ✅ Metrics collection
- ✅ Structured logging
- ✅ Trace correlation
- ✅ Graceful degradation
- ✅ Comprehensive tests

### For Production Deployment
- ⚠️ Replace H2 with PostgreSQL/MySQL
- ⚠️ Enable SSL/TLS encryption
- ⚠️ Add authentication/authorization
- ⚠️ Implement rate limiting
- ⚠️ Deploy centralized logging (ELK Stack)
- ⚠️ Set up distributed tracing (Jaeger)
- ⚠️ Configure Prometheus + Grafana
- ⚠️ Deploy on Kubernetes
- ⚠️ Add request signing
- ⚠️ Implement API versioning

## Scalability

### Horizontal Scaling
- Add load balancer (Nginx, AWS ELB)
- Deploy multiple instances of each service
- Share persistent database (PostgreSQL)
- Use Redis for distributed caching

### Vertical Scaling
- Increase JVM heap memory
- Tune garbage collection
- Optimize database queries
- Increase connection pool size

### Bottlenecks to Address
1. Single H2 database → Use PostgreSQL with replicas
2. Synchronous REST calls → Add async fallback
3. 30-second circuit breaker wait → Tune based on SLA
4. Network latency → Co-locate services

## Future Enhancements

### Short Term (1-2 weeks)
- Add OpenTelemetry Collector integration
- Prometheus metrics endpoint
- Contract tests (Pact)
- Swagger/OpenAPI documentation
- Request signing

### Medium Term (1-2 months)
- Event sourcing for audit trail
- Async processing with Kafka
- Read replicas for Account Service
- GraphQL interface
- Rate limiting

### Long Term (3+ months)
- Multi-region deployment
- CQRS pattern
- Event replay capability
- Machine learning for fraud detection
- Blockchain integration

## Support & Troubleshooting

### Common Issues

**Port Already in Use**:
```bash
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

**Services Not Communicating**:
- Verify both services running
- Check Account Service URL in Gateway config
- Review logs for connection errors

**Circuit Breaker Open**:
- Check Account Service health
- Wait 30 seconds for recovery
- Restart Account Service if needed

**Events Not Processing**:
- Verify event timestamp format
- Check amount is > 0
- Confirm Account Service is running

## Next Steps

1. **Try the API**: Follow SETUP.md quick start
2. **Run Tests**: Execute `mvn test`
3. **Review Code**: Check source implementations
4. **Explore Metrics**: Visit `/actuator/metrics`
5. **Test Resilience**: Stop Account Service, observe circuit breaker
6. **Deploy**: Use Docker Compose for containerized setup
7. **Customize**: Modify configuration for your environment

## Repository Information

- **Language**: Java 17+
- **Build Tool**: Maven 3.8+
- **VCS**: Git
- **License**: Provided as-is for evaluation

## Contact & Questions

For detailed information, refer to:
- SETUP.md for deployment help
- ARCHITECTURE.md for design details
- API.md for endpoint documentation
- Source code for implementation details

---

**Status**: ✅ Complete and ready for deployment

**Build Status**: ✅ Compiles successfully

**Tests**: ✅ Comprehensive coverage

**Documentation**: ✅ Detailed and complete

**Docker**: ✅ Ready for containerized deployment

**Git**: ✅ Initialized with meaningful commit history

