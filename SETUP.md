# Setup and Deployment Guide

## Prerequisites

### System Requirements
- **Java 17 or higher**
  - Download: https://adoptium.net/
  - Verify: `java -version`
  
- **Maven 3.8.0 or higher**
  - Download: https://maven.apache.org/download.cgi
  - Verify: `mvn -version`
  
- **Docker & Docker Compose** (for containerized deployment)
  - Download: https://www.docker.com/products/docker-desktop
  - Verify: `docker --version` and `docker-compose --version`

- **Git** (recommended for version control)
  - Download: https://git-scm.com/

## Quick Start (Docker - Recommended)

The easiest way to get started is using Docker Compose:

```bash
# Clone or navigate to the project
cd Event-Ledger

# Build and start both services
docker-compose up --build

# Wait for services to be healthy (look for "event-gateway-api_1 | ... Started" messages)
```

Services will be available at:
- **Event Gateway API**: http://localhost:8080
- **Account Service**: http://localhost:8081

To stop services:
```bash
docker-compose down
```

## Local Development Setup

### Step 1: Clone the Repository
```bash
git clone <repository-url>
cd Event-Ledger
```

### Step 2: Build the Project
```bash
mvn clean install -DskipTests
```

This will:
- Build the common module (shared utilities)
- Build the account-service module
- Build the event-gateway-api module
- Install all JAR files locally

### Step 3: Start Account Service (First Terminal)
```bash
cd account-service
java -jar target/account-service-1.0.0.jar
```

Expected output:
```
Started AccountServiceApplication in X.XXX seconds (JVM running for X.XXX)
```

The service will be available at: `http://localhost:8081`

### Step 4: Start Event Gateway API (Second Terminal)
```bash
cd event-gateway-api
java -jar target/event-gateway-api-1.0.0.jar
```

Expected output:
```
Started EventGatewayApplication in X.XXX seconds (JVM running for X.XXX)
```

The service will be available at: `http://localhost:8080`

## Verification

### Check Service Health

**Event Gateway:**
```bash
curl http://localhost:8080/events/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "event-gateway-api",
  "timestamp": 1715780535000
}
```

**Account Service:**
```bash
curl http://localhost:8081/accounts/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "account-service",
  "timestamp": 1715780535000
}
```

### Test Event Submission

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "test-evt-001",
    "accountId": "test-acct-001",
    "type": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-08T14:00:00Z",
    "metadata": {"source": "test"}
  }'
```

Expected response (201 Created):
```json
{
  "eventId": "test-evt-001",
  "accountId": "test-acct-001",
  "type": "CREDIT",
  "amount": 100.00,
  "currency": "USD",
  "eventTimestamp": "2026-06-08T14:00:00Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Module Tests
```bash
# Event Gateway tests
mvn test -pl event-gateway-api

# Account Service tests
mvn test -pl account-service

# Common module tests
mvn test -pl common
```

### Run Specific Test Class
```bash
mvn test -Dtest=EventServiceTest
mvn test -Dtest=EventGatewayIntegrationTest
mvn test -Dtest=AccountServiceIntegrationTest
```

### Generate Test Report
```bash
mvn test jacoco:report
# Report available at: target/site/jacoco/index.html
```

## Configuration

### Event Gateway Configuration
File: `event-gateway-api/src/main/resources/application.yml`

Key settings:
- `server.port`: 8080
- `account-service.url`: http://localhost:8081 (adjust if Account Service is on different host)
- Circuit breaker settings for resilience

### Account Service Configuration
File: `account-service/src/main/resources/application.yml`

Key settings:
- `server.port`: 8081
- Database: H2 in-memory

### Custom Configuration

To override configurations at startup:

**Using Environment Variables:**
```bash
java -jar event-gateway-api/target/event-gateway-api-1.0.0.jar \
  --account-service.url=http://account-service:8081 \
  --server.port=8080
```

**Using Properties File:**
Create `application-custom.properties` and run with:
```bash
java -jar event-gateway-api-1.0.0.jar \
  --spring.config.additional-location=file:./application-custom.properties
```

## Monitoring and Diagnostics

### View Application Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### View Specific Metric
```bash
curl http://localhost:8080/actuator/metrics/events.submitted
```

### Check Circuit Breaker Status
```bash
curl http://localhost:8080/actuator/health/circuitbreakers
```

### View Logs
Logs include trace IDs for request correlation:
```
2026-06-08 14:05:35 - Received event evt-001 [traceId=abc-123]
2026-06-08 14:05:35 - Event processing completed [traceId=abc-123]
```

## Troubleshooting

### Port Already in Use
If you get "Address already in use" error:

```bash
# Windows - Find process using port 8080
netstat -ano | findstr :8080

# Kill process (replace PID with the actual PID)
taskkill /PID <PID> /F

# Or use different ports
java -jar account-service-1.0.0.jar --server.port=8081
java -jar event-gateway-api-1.0.0.jar --server.port=8080 --account-service.url=http://localhost:8081
```

### Services Not Communicating
```bash
# Verify both services are running
curl http://localhost:8080/events/health
curl http://localhost:8081/accounts/health

# Check logs for "Connection refused" errors
# Verify account-service.url points to correct host:port
```

### Circuit Breaker Open
If you see "Circuit breaker is open":
1. Check Account Service logs
2. Verify Account Service is running and healthy
3. Wait 30 seconds for circuit breaker to transition to half-open state
4. Or restart Account Service

### Events Not Processing
Check:
1. Event timestamp is ISO 8601 format
2. Amount is greater than 0
3. Type is CREDIT or DEBIT
4. Account Service is healthy
5. Check logs with trace ID for debugging

## Database

Both services use H2 in-memory databases by default:

- **Event Gateway**: `jdbc:h2:mem:event_gateway_db`
- **Account Service**: `jdbc:h2:mem:account_service_db`

H2 Console is available (for debugging):
- Event Gateway: http://localhost:8080/h2-console
- Account Service: http://localhost:8081/h2-console

Login with:
- JDBC URL: `jdbc:h2:mem:event_gateway_db` (or `account_service_db`)
- User: `sa`
- Password: (leave empty)

## Production Deployment

For production deployment, consider:

1. **Use Persistent Databases**: Replace H2 with PostgreSQL or MySQL
2. **Configure SSL/TLS**: Enable HTTPS communication
3. **Environment-Specific Config**: Use profiles (prod, staging, dev)
4. **Centralized Logging**: Set up ELK Stack or similar
5. **Distributed Tracing**: Set up Jaeger or Zipkin
6. **Monitoring**: Deploy Prometheus + Grafana
7. **Load Balancing**: Use Nginx or AWS ELB
8. **Container Orchestration**: Use Kubernetes or ECS

### Production Configuration Example

Create `application-prod.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod-db-host:5432/event_ledger
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20

server:
  port: 8080
  ssl:
    key-store: /etc/ssl/keystore.jks
    key-store-password: ${KEYSTORE_PASSWORD}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

## Performance Tuning

### JVM Settings
```bash
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar event-gateway-api-1.0.0.jar
```

### Database Connection Pool
Adjust in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

## Version Control

Initial commits have been made with the following history:
1. Initial project structure with parent POM and modules
2. Add comprehensive integration tests for both services

To view commit history:
```bash
git log --oneline
```

To push to remote repository:
```bash
git remote add origin <repository-url>
git push -u origin main
```

## Support

For issues:
1. Check the troubleshooting section above
2. Review service logs (look for trace IDs)
3. Verify health endpoints
4. Test API endpoints with curl or Postman
5. Check that Account Service is running when testing Event Gateway

## Next Steps

1. **Try the API**: Follow the "Verification" section above
2. **Run Tests**: Execute `mvn test`
3. **Review Code**: Check the source in `event-gateway-api/` and `account-service/`
4. **Explore Metrics**: Visit `/actuator/metrics` endpoints
5. **Test Resilience**: Stop Account Service and observe circuit breaker behavior

