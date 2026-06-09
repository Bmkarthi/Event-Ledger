# Event Ledger - Distributed Microservices System

A production-ready distributed event ledger system built with Spring Boot, featuring two independent microservices that process financial transaction events reliably with distributed tracing, resilience, observability, and graceful degradation.

---

# Architecture Overview

The Event Ledger system consists of two independent Spring Boot microservices:

## Event Gateway API (Port 8080)

Responsibilities:

* Public-facing service
* Accepts transaction events
* Validates incoming requests
* Enforces idempotency using eventId
* Stores event records in its own H2 database
* Calls Account Service synchronously
* Handles resiliency using Resilience4j Circuit Breaker

Endpoints:

* POST /events
* GET /events/{id}
* GET /events?account={accountId}
* GET /health

## Account Service (Port 8081)

Responsibilities:

* Internal service
* Maintains account balances
* Stores transaction history
* Stores account data in its own H2 database
* Processes CREDIT and DEBIT transactions

Endpoints:

* POST /accounts/{accountId}/transactions
* GET /accounts/{accountId}/balance
* GET /accounts/{accountId}
* GET /health

## Communication Flow
# Event Ledger - Distributed Microservices System

Event Ledger is a small, production-oriented sample of a distributed microservice
system implemented with Spring Boot. It demonstrates event submission, durable
persistence, idempotent transaction application and resilient inter-service
communication between two services:

- Event Gateway API (port 8080): accepts events, persists them, and coordinates
  applying transactions to accounts via the Account Service.
- Account Service (port 8081): responsible for storing accounts and applying
  transactions (idempotent).

This repository includes simple, testable implementations and demonstrates
trace propagation, structured logging, metrics and a Resilience4j-based
resiliency strategy.

---

Table of contents
- Architecture overview
- Prerequisites
- Setup & configuration
- How to start (Docker and manual)
- API endpoints and examples
- Running tests
- Resiliency design and configuration
- Observability (logs, traces, metrics)
- Project structure (what's in each module)
- Troubleshooting

---

Architecture overview
---------------------
The system contains two independent microservices that communicate over HTTP:

- Event Gateway API (frontend): Receives events via REST and persists them to
  a local H2 store. For each new event the gateway attempts to apply a
  corresponding transaction by calling the Account Service. If the Account
  Service is unavailable, the gateway keeps the event in PENDING state and
  records a metric; retries and circuit breaker behavior are configured to
  protect the gateway and downstream systems.

- Account Service (backend): Stores account records and a list of applied
  transactions. It supports idempotent application of transactions via an
  idempotency key included with each request.

Trace propagation
- The `X-Trace-ID` HTTP header is used to propagate a trace id across service
  boundaries. If a caller doesn't provide one, services generate a UUID and
  include it in both the structured logs and API responses.

Ports
- Event Gateway API: http://localhost:8080
- Account Service: http://localhost:8081

---

Prerequisites
-------------
- Java 17 or newer
- Maven 3.6+
- Docker and Docker Compose (recommended for local all-in-one runs)
- On Windows use PowerShell; ensure Docker Desktop is running before starting
  the compose stack.

---

Setup & configuration
---------------------
1. Clone the repository:

```powershell
git clone <repo-url> C:\Microservices\Event-Ledger
cd C:\Microservices\Event-Ledger
```

2. Build the project (quick build, tests skipped):

```powershell
mvn -DskipTests package
```

3. Configuration files
- Each service contains `src/main/resources/application.yml`. You can change
  ports, Resilience4j settings, and logging configuration there. The
  `docker-compose.yml` provided sets environment variables and ports used by
  the Docker run mode.

---

How to start
------------
Recommended: Docker Compose (single step)

```powershell
cd C:\Microservices\Event-Ledger
docker-compose up --build
```

This starts both services and any supporting components configured in
`docker-compose.yml`. After startup the services are available at the ports
noted above.

Manual (run locally without Docker)

```powershell
cd C:\Microservices\Event-Ledger
mvn clean package -DskipTests

# Terminal 1: Start Account Service
cd account-service ; java -jar target/account-service-1.0.0.jar

# Terminal 2: Start Event Gateway
cd event-gateway-api ; java -jar target/event-gateway-api-1.0.0.jar
```

Notes:
- If a required port is already in use, change it in the service's
  `application.yml` before starting.
- Use `-Dspring.profiles.active=...` to load an alternative profile if you
  maintain different configs.

---

API endpoints and examples
--------------------------
Submit an event (POST /events)

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-ID: trace-123" \
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

Get events for an account (GET /events?account={id})

```bash
curl "http://localhost:8080/events?account=acct-123"
```

Get a specific event (GET /events/{id})

```bash
curl http://localhost:8080/events/evt-001
```

Account service health

```bash
curl http://localhost:8081/accounts/health
```

All public endpoints are defined in the controllers under each module
(`event-gateway-api/src/main/java/.../controller` and
`account-service/src/main/java/.../controller`). Responses include a `traceId`
field for correlation.

---

Running tests
-------------
Run the full test suite:

```powershell
mvn test
```

Run tests for a single module:

```powershell
mvn -pl account-service test
mvn -pl event-gateway-api test
```

Notes:
- Integration tests and unit tests are included; some tests rely on
  Spring Boot test slices and will run faster when executed module-by-module.

---

Resiliency design (why and how)
--------------------------------
The gateway uses Resilience4j (circuit breaker, retry and time limiter) when
calling the Account Service. The main motivations are:

- Fail fast and avoid cascading failures when the Account Service is down.
- Reduce resource consumption on failing downstream systems.
- Allow graceful degradation: events are persisted and kept in PENDING state
  so they can be retried later rather than lost.

Configuration notes:
- Resilience4j settings are kept in `application.yml` of the gateway module
  (check `event-gateway-api/src/main/resources/application.yml`). Typical
  tuneable values are failure rate threshold, wait duration when open, and
  permitted number of trial calls in half-open state.

Fallback behavior
- When circuit breaker conditions are met the `AccountServiceClient` will
  execute a fallback that fails fast with a typed exception. The gateway
  translates this into a suitable HTTP response and persists the event with a
  PENDING state so it can be processed when the backend recovers.

---

Observability
-------------
- Structured logging: the project includes a `StructuredLogger` utility that
  logs contextual JSON for easier parsing by log aggregators.
- Tracing: the `X-Trace-ID` header is used to correlate logs across services.
- Metrics: Micrometer collectors are used to record counters such as
  `events.submitted`, `events.processed`, `transactions.applied`, etc.

When debugging production-like scenarios start by:
1. Checking the `traceId` from the REST response and searching logs.
2. Inspecting metrics (if exposed) for spikes in failure counters.
3. Reviewing circuit breaker state in logs if Resilience4j exposes it.

---

Project structure (what's in each module)
----------------------------------------
Event-Ledger/
- common/ - shared utilities (logging, tracing helpers)
- event-gateway-api/ - gateway service (controllers, service, client, repo)
- account-service/ - account service (controllers, service, repo)
- docker-compose.yml - composition for local environment

Files of interest
- `docker-compose.yml` - start both services quickly
- `event-gateway-api/src/main/resources/application.yml` - gateway config
- `account-service/src/main/resources/application.yml` - account config
- `common/src/main/java/com/eventledger/common/logging/StructuredLogger.java`

---

Troubleshooting
---------------
- Port conflicts: modify `server.port` in the service `application.yml`.
- Docker issues on Windows: ensure Docker Desktop is running and WSL2 is
  enabled (if applicable).
- Build problems: run `mvn -DskipTests package` to produce JARs without
  running tests and inspect compiler errors.

If you run into unexpected failures when invoking the gateway while the
Account Service is down, check the gateway logs for Resilience4j messages and
the event repository for PENDING events.

---

Contributing and next steps
---------------------------
If you want improvements, consider:

- Adding more comprehensive integration tests that spin up both services via
  Testcontainers.
- Implementing background re-delivery of PENDING events once the Account
  Service becomes available.
- Exporting metrics to Prometheus and adding a Grafana dashboard for
  visualization.

---

License and contact
-------------------
This repository is provided as-is for demonstration purposes. Refer to the
project root for any license file or contributor instructions.
* Spring Boot 3.2
* Spring Data JPA
* H2 Database
* Resilience4j
* OpenTelemetry
* Micrometer
* Maven
* Docker Compose
* JUnit 5
* Mockito

---

# Assumptions

* eventId is globally unique.
* Account balances are computed as CREDIT minus DEBIT.
* Event ordering is based on eventTimestamp rather than arrival order.
* Services maintain independent databases and do not share state.
* Communication between services is synchronous REST.
