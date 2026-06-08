# Event Ledger API Documentation

## Base URLs

- **Event Gateway**: `http://localhost:8080`
- **Account Service**: `http://localhost:8081`

## Common Headers

All requests should include:
```
Content-Type: application/json
X-Trace-ID: <optional, auto-generated if not provided>
```

## Common Response Format

### Success Response
```json
{
  "data": { /* response data */ },
  "traceId": "uuid-string",
  "timestamp": "2026-06-08T14:05:35.000Z"
}
```

### Error Response
```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "traceId": "uuid-string",
  "statusCode": 400
}
```

---

## Event Gateway API

### 1. Submit Event

**Endpoint**: `POST /events`

**Description**: Submit a transaction event to the system. Supports idempotency - duplicate eventId submissions return the original event.

**Request Headers**:
```
Content-Type: application/json
X-Trace-ID: optional-trace-id (auto-generated if not provided)
```

**Request Body**:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

**Field Descriptions**:
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| eventId | string | Yes | Unique identifier for the event. Must be unique across all submissions. |
| accountId | string | Yes | The account this event belongs to. |
| type | string | Yes | Transaction type. Must be `CREDIT` or `DEBIT`. |
| amount | number | Yes | Transaction amount. Must be > 0. |
| currency | string | Yes | ISO 4217 currency code (e.g., "USD", "EUR", "GBP"). |
| eventTimestamp | string (ISO 8601) | Yes | When the event originally occurred. Format: `2026-05-15T14:02:11Z` |
| metadata | object | No | Optional additional context. Can contain any JSON fields. |

**Response - Success (201 Created)**:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

**Response - Duplicate (201 Created)** - Idempotent response:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

**Response - Validation Error (400 Bad Request)**:
```json
{
  "error": "Validation failed",
  "message": "Validation errors: amount must be greater than 0, type must be CREDIT or DEBIT",
  "traceId": "trace-abc123"
}
```

**Response - Account Service Unavailable (503 Service Unavailable)**:
```json
{
  "error": "Account Service is currently unavailable",
  "message": "Event has been created with PENDING status",
  "traceId": "trace-abc123"
}
```

**Examples**:

Successful submission:
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
    "metadata": {"source": "batch"}
  }'
```

With custom trace ID:
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-ID: my-trace-001" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "DEBIT",
    "amount": 50.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:00:00Z"
  }'
```

---

### 2. Get Event by ID

**Endpoint**: `GET /events/{id}`

**Description**: Retrieve a specific event by its ID. This works even when Account Service is unavailable.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | string | Yes | The eventId to retrieve |

**Request Headers**:
```
X-Trace-ID: optional-trace-id
```

**Response - Success (200 OK)**:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

**Response - Not Found (404 Not Found)**:
```json
{
  "error": "Not found",
  "message": "Event not found: evt-999",
  "traceId": "trace-abc123"
}
```

**Examples**:
```bash
# Get event
curl http://localhost:8080/events/evt-001

# Get event with trace ID
curl -H "X-Trace-ID: my-trace-001" http://localhost:8080/events/evt-001
```

---

### 3. List Events by Account

**Endpoint**: `GET /events?account={accountId}`

**Description**: Retrieve all events for a specific account, ordered by event timestamp (earliest first). This works even when Account Service is unavailable.

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| account | string | Yes | The accountId to filter by |

**Request Headers**:
```
X-Trace-ID: optional-trace-id
```

**Response - Success (200 OK)**:
```json
[
  {
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:00:00Z",
    "status": "PROCESSED",
    "createdAt": "2026-06-08T14:05:35.000Z"
  },
  {
    "eventId": "evt-002",
    "accountId": "acct-123",
    "type": "DEBIT",
    "amount": 30.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:10:00Z",
    "status": "PROCESSED",
    "createdAt": "2026-06-08T14:05:36.000Z"
  }
]
```

**Response - Success (Empty List)**:
```json
[]
```

**Examples**:
```bash
# List events for account
curl "http://localhost:8080/events?account=acct-123"

# With trace ID
curl -H "X-Trace-ID: my-trace-001" "http://localhost:8080/events?account=acct-123"
```

---

### 4. Health Check

**Endpoint**: `GET /events/health`

**Description**: Check the health status of the Event Gateway API.

**Response - Success (200 OK)**:
```json
{
  "status": "UP",
  "service": "event-gateway-api",
  "timestamp": 1715780535000
}
```

**Response - Service Down (503 Service Unavailable)**:
```json
{
  "status": "DOWN",
  "service": "event-gateway-api",
  "details": "Database connection failed",
  "timestamp": 1715780535000
}
```

---

## Account Service API

### 1. Apply Transaction

**Endpoint**: `POST /accounts/{accountId}/transactions`

**Description**: Apply a transaction (CREDIT or DEBIT) to an account. Supports idempotency - duplicate requests with the same idempotencyKey are ignored.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | string | Yes | The account ID to apply transaction to |

**Request Headers**:
```
Content-Type: application/json
X-Trace-ID: optional-trace-id
```

**Request Body**:
```json
{
  "type": "CREDIT",
  "amount": 100.00,
  "currency": "USD",
  "idempotencyKey": "evt-001"
}
```

**Field Descriptions**:
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| type | string | Yes | Must be `CREDIT` or `DEBIT` |
| amount | number | Yes | Amount to transfer. Must be > 0. |
| currency | string | Yes | Currency code (e.g., "USD") |
| idempotencyKey | string | Yes | Unique key for idempotency. Usually the eventId from Event Gateway. |

**Response - Success (200 OK)**:
```json
{
  "status": "SUCCESS",
  "accountId": "acct-123",
  "traceId": "trace-abc123"
}
```

**Response - Duplicate (200 OK)** - Idempotent, not re-applied:
```json
{
  "status": "SUCCESS",
  "accountId": "acct-123",
  "traceId": "trace-abc123"
}
```

**Response - Error (500 Internal Server Error)**:
```json
{
  "error": "Transaction failed",
  "message": "Failed to apply transaction",
  "traceId": "trace-abc123"
}
```

**Examples**:
```bash
# Apply credit
curl -X POST http://localhost:8081/accounts/acct-123/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "idempotencyKey": "evt-001"
  }'

# Apply debit
curl -X POST http://localhost:8081/accounts/acct-123/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "DEBIT",
    "amount": 50.00,
    "currency": "USD",
    "idempotencyKey": "evt-002"
  }'
```

---

### 2. Get Account Balance

**Endpoint**: `GET /accounts/{accountId}/balance`

**Description**: Get the current balance for an account. Returns 0 if account doesn't exist.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | string | Yes | The account ID |

**Request Headers**:
```
X-Trace-ID: optional-trace-id
```

**Response - Success (200 OK)**:
```json
{
  "accountId": "acct-123",
  "balance": 500.00,
  "traceId": "trace-abc123"
}
```

**Response - Account Not Found (200 OK)** - Returns zero balance:
```json
{
  "accountId": "acct-999",
  "balance": 0.00,
  "traceId": "trace-abc123"
}
```

**Examples**:
```bash
# Get balance
curl http://localhost:8081/accounts/acct-123/balance

# Get balance with trace ID
curl -H "X-Trace-ID: my-trace-001" http://localhost:8081/accounts/acct-123/balance
```

---

### 3. Get Account Details

**Endpoint**: `GET /accounts/{accountId}`

**Description**: Get full account details including balance and transaction history summary.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| accountId | string | Yes | The account ID |

**Request Headers**:
```
X-Trace-ID: optional-trace-id
```

**Response - Success (200 OK)**:
```json
{
  "accountId": "acct-123",
  "balance": 500.00,
  "createdAt": "2026-06-08T14:05:35.000Z",
  "updatedAt": "2026-06-08T14:10:35.000Z",
  "transactionCount": 3,
  "traceId": "trace-abc123"
}
```

**Response - Not Found (404 Not Found)**:
```json
{
  "error": "Account not found",
  "accountId": "acct-999",
  "traceId": "trace-abc123"
}
```

**Examples**:
```bash
# Get account details
curl http://localhost:8081/accounts/acct-123

# Get with trace ID
curl -H "X-Trace-ID: my-trace-001" http://localhost:8081/accounts/acct-123
```

---

### 4. Health Check

**Endpoint**: `GET /accounts/health`

**Description**: Check the health status of the Account Service.

**Response - Success (200 OK)**:
```json
{
  "status": "UP",
  "service": "account-service",
  "timestamp": 1715780535000
}
```

**Response - Service Down (503 Service Unavailable)**:
```json
{
  "status": "DOWN",
  "service": "account-service",
  "details": "Database connection failed",
  "timestamp": 1715780535000
}
```

---

## Request/Response Examples

### Complete Flow Example

**Step 1: Submit Event**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Trace-ID: flow-001" \
  -d '{
    "eventId": "evt-flow-001",
    "accountId": "acct-flow-001",
    "type": "CREDIT",
    "amount": 500.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-08T14:00:00Z"
  }'
```

Response:
```json
{
  "eventId": "evt-flow-001",
  "accountId": "acct-flow-001",
  "type": "CREDIT",
  "amount": 500.00,
  "currency": "USD",
  "eventTimestamp": "2026-06-08T14:00:00Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

**Step 2: Get Event**
```bash
curl -H "X-Trace-ID: flow-001" \
  http://localhost:8080/events/evt-flow-001
```

Response:
```json
{
  "eventId": "evt-flow-001",
  "accountId": "acct-flow-001",
  "type": "CREDIT",
  "amount": 500.00,
  "currency": "USD",
  "eventTimestamp": "2026-06-08T14:00:00Z",
  "status": "PROCESSED",
  "createdAt": "2026-06-08T14:05:35.000Z"
}
```

**Step 3: Get Account Balance**
```bash
curl -H "X-Trace-ID: flow-001" \
  http://localhost:8081/accounts/acct-flow-001/balance
```

Response:
```json
{
  "accountId": "acct-flow-001",
  "balance": 500.00,
  "traceId": "flow-001"
}
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Scenarios |
|------|---------|-----------|
| 200 | OK | Successful GET or POST operation |
| 201 | Created | Event successfully created |
| 400 | Bad Request | Invalid input, missing fields, validation failed |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Idempotency check (when returning existing resource) |
| 500 | Internal Server Error | Unexpected server error |
| 503 | Service Unavailable | Account Service unreachable (for POST /events) |

### Validation Errors

Typical validation error scenarios:

```bash
# Missing required field
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"accountId": "acct-123"}'

# Response: 400 Bad Request
# {"error": "Validation failed", "message": "Validation errors: eventId is required, type is required, amount is required, ..."}
```

```bash
# Invalid type
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "TRANSFER",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-08T14:00:00Z"
  }'

# Response: 400 Bad Request
# {"error": "Validation failed", "message": "type must be CREDIT or DEBIT"}
```

```bash
# Invalid amount
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": -100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-08T14:00:00Z"
  }'

# Response: 400 Bad Request
# {"error": "Validation failed", "message": "amount must be greater than 0"}
```

---

## Rate Limiting & Circuit Breaker

### Circuit Breaker States

When the Account Service is down, the Event Gateway circuit breaker will:

**Closed (Normal)**:
- All requests to Account Service succeed
- Events processed successfully

**Open (Service Down)**:
- Account Service calls fail immediately with 503
- Circuit breaker prevents repeated calls
- Events created with PENDING status
- After 30 seconds, transitions to Half-Open

**Half-Open (Recovery Testing)**:
- Up to 3 test calls allowed to Account Service
- If successful: transitions to Closed
- If failed: transitions back to Open

---

## Testing the API

### Using cURL

```bash
# Health checks
curl http://localhost:8080/events/health
curl http://localhost:8081/accounts/health

# Submit event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{...}'

# Get events
curl "http://localhost:8080/events?account=acct-123"

# Get balance
curl http://localhost:8081/accounts/acct-123/balance
```

### Using Postman

Import the following collection:
```json
{
  "info": {
    "name": "Event Ledger API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Submit Event",
      "request": {
        "method": "POST",
        "url": "{{base_url}}/events",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {"raw": "{...}"}
      }
    }
  ]
}
```

### Using Python Requests

```python
import requests
import json

url = "http://localhost:8080/events"
headers = {"Content-Type": "application/json", "X-Trace-ID": "py-001"}
data = {
    "eventId": "evt-py-001",
    "accountId": "acct-py-001",
    "type": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-08T14:00:00Z"
}

response = requests.post(url, json=data, headers=headers)
print(json.dumps(response.json(), indent=2))
```

---

## Notes

- All timestamps are in ISO 8601 format (UTC)
- Amounts are decimal with 4 decimal places (e.g., 100.0000)
- Trace IDs are used for request correlation across services
- Idempotency is guaranteed for duplicate submissions within reasonable timeframes
- Balance calculations are always accurate regardless of event arrival order

