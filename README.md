# Event-Driven Order Processing System

A small, production-shaped system for placing an order, checking/reserving inventory, and notifying the customer of the outcome — built to demonstrate patterns commonly used in real event-driven microservice systems, without the sprawl of a system nobody will actually finish or re-read.

## Architecture

```text
                      ┌────────────────┐
   client ──JWT────▶  │  API Gateway   │  (Spring Cloud Gateway, reactive)
                      │  /auth/login   │  issues JWTs
                      └───────┬────────┘
                              │ routes + validates JWT at the edge
              ┌───────────────┴────────────────┐
              ▼                                ▼
      ┌───────────────┐                ┌────────────────────┐
      │ Order Service │                │ Inventory Service  │
      │ (Postgres)    │                │ (Postgres)          │
      └───────┬───────┘                └──────────┬─────────┘
              │  order-created                    │
              ▼                                   │
      ┌────────────────────────────────────────────┴──────┐
      │                  Kafka + Schema Registry           │
      │   order-created · inventory-reserved ·            │
      │   inventory-failed · order-confirmed              │
      └───────┬───────────────────────────────────┬───────┘
              │ inventory-reserved / -failed       │ order-confirmed
              ▼                                    ▼
      ┌───────────────┐                  ┌─────────────────────┐
      │ Order Service │                  │ Notification Service│
      │ (confirms/    │                  │ (logs / would call  │
      │  fails order) │                  │  email/SMS provider)│
      └───────────────┘                  └─────────────────────┘
```

**4 services, on purpose** — API Gateway, Order Service, Inventory Service, Notification Service. No separate User Service: the gateway owns JWT issuance against an in-memory user store, which keeps the authentication story coherent without a fifth service and its own database.

### Event flow

1. `POST /api/v1/orders` → Order Service persists the order as `PENDING_INVENTORY` and publishes **`order-created`** (Avro, validated against Schema Registry), keyed by `orderId`.

2. Inventory Service consumes `order-created`, tries to reserve every line item in one DB transaction, and publishes either **`inventory-reserved`** or **`inventory-failed`**.

3. Order Service consumes both outcomes, moves the order to `CONFIRMED` or `FAILED`, and publishes **`order-confirmed`**, carrying the final order status and keyed by `orderId`.

4. Notification Service consumes `order-confirmed` and dispatches a notification (stubbed as a log line — swap in a real email/SMS/push client without changing the listener contract).

All four topics key events by `orderId`, so every event for a given order lands on the same partition in each topic and is processed in order relative to that order — without requiring a global ordering guarantee across unrelated orders.

## Why these design choices (interview talking points)

* **Kafka + Avro + Schema Registry.** Every event is a generated Avro class (`common-avro` module), not a hand-maintained JSON DTO. Schema Registry validates event schemas and can enforce the configured compatibility policy, helping prevent incompatible schema changes between producers and consumers.

* **Consumer groups.** `order-service`, `inventory-service`, and `notification-service` are independent consumer groups. Each group tracks its own offsets, so notification-service does not "steal" messages inventory-service needs — each group receives its own independent view of the stream.

* **Partitions & ordering.** Kafka only guarantees order *within* a partition. Keying every event by `orderId` gives us ordering for events belonging to the same order without forcing the entire topic through a single partition, which would unnecessarily limit throughput.

* **Idempotency.** At-least-once delivery means a consumer can see a redelivered message after a crash, rebalance, or failed offset commit. Two different idempotency techniques are used deliberately:

  * **Inventory Service** keeps a `processed_order_events` table and checks/records the `orderId` inside the *same transaction* as the stock decrement, providing an exactly-once-effect pattern over an at-least-once transport.
  * **Order Service** relies on the aggregate's state machine: `confirmOrder` / `failOrder` are no-ops unless the order is still `PENDING_INVENTORY`, so a duplicate inventory event after the order is already finalized is safely ignored.

* **Offsets & delivery semantics.** `ack-mode: RECORD` commits the offset only after the listener method returns normally, giving at-least-once delivery rather than at-most-once delivery. The trade-off is possible redelivery, which is why idempotency is important.

* **Resilience.** Kafka publishes go through Resilience4j `@Retry` with exponential backoff, wrapped in a `@CircuitBreaker`, with a fallback that logs. A production implementation could replace the logging fallback with a transactional outbox for durable replay.

* **Concurrency control.** `Order` uses JPA optimistic locking (`@Version`) because REST-triggered cancellation and Kafka-triggered confirmation could race. `InventoryItem` uses a pessimistic row lock (`SELECT ... FOR UPDATE`) because overselling is a correctness problem, and pessimistic locking avoids the retry-loop complexity that optimistic locking could require under heavy contention.

* **Defense in depth on auth.** The gateway validates the JWT and rejects invalid requests at the edge, but Order Service and Inventory Service also independently validate every JWT. A downstream service never blindly trusts an authentication header simply because a request passed through the gateway.

## Tech stack

Java 21 · Spring Boot 3.3 · Spring Cloud Gateway (reactive) · Spring Security + JWT (JJWT) · Spring Data JPA/Hibernate · Kafka + Avro + Confluent Schema Registry · PostgreSQL · Flyway · Resilience4j (retry + circuit breaker) · Docker + Docker Compose · JUnit 5 + Mockito + AssertJ + Testcontainers · Maven (multi-module reactor) · GitHub Actions

## Repository layout

```text
order-processing-system/

├── common-avro/             Avro schemas, shared by every service via avro-maven-plugin
├── order-service/           REST + JPA + Kafka producer/consumer + JWT verification
├── inventory-service/       Kafka consumer/producer + JPA + JWT verification
├── notification-service/    Kafka consumer only, no DB
├── api-gateway/             Reactive gateway: JWT issuance + routing + edge auth
├── docker-compose.yml       Kafka, Zookeeper, Schema Registry, 2x Postgres, all 4 services
├── .env                     Local-only environment variables (ignored by Git)
└── .github/workflows/ci.yml
```

## Running it locally

```bash
# 1. Build & install the shared Avro module first
#    (everything else depends on it)
mvn -pl common-avro -am install -DskipTests

# 2. Bring the whole stack up
docker compose up --build
```

The local development stack uses HashiCorp Vault in **development mode** for simplicity. The Vault token and other local-only credentials are supplied through `.env`, which is intentionally ignored by Git and must not be committed.

Services come up on:

| Service              | Port |
| -------------------- | ---- |
| api-gateway          | 8080 |
| order-service        | 8081 |
| inventory-service    | 8082 |
| notification-service | 8083 |
| schema-registry      | 8085 |
| kafka-ui             | 8090 |
| kafka (host access)  | 9092 |
| vault                | 8200 |

Inventory Service is seeded via Flyway with demo stock for `SKU-1`, `SKU-2`, and `SKU-3`.

### Try the happy path

```bash
# Get a token (demo users: alice/password123, admin/adminpass123)
TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | jq -r .accessToken)

# Create an order
curl -s -X POST localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"sku":"SKU-1","quantity":2,"price":10.00}]}'

# Watch it move from PENDING_INVENTORY to its final state
curl -s localhost:8080/api/v1/orders/1 \
  -H "Authorization: Bearer $TOKEN"
```

A successful inventory reservation results in:

```text
PENDING_INVENTORY → CONFIRMED
```

Requesting more stock than is available (for example, `999` of `SKU-3`, seeded at `5`) demonstrates the failure path:

```text
PENDING_INVENTORY
       ↓
inventory-failed
       ↓
FAILED
       ↓
order-confirmed (final status = FAILED)
```

You can also inspect the notification flow with:

```bash
docker compose logs -f notification-service
```

## Running tests

```bash
mvn -pl common-avro -am install -DskipTests

mvn -pl order-service -am test
# Mockito unit tests + MockMvc slice + Testcontainers Postgres IT

mvn -pl inventory-service -am test
# Mockito unit tests for reservation atomicity/idempotency

mvn -pl notification-service -am test

mvn -pl api-gateway -am test
# JwtUtil + WebTestClient authentication flow tests
```

CI (`.github/workflows/ci.yml`) runs the same test sequence and then builds every service's Docker image on pushes and pull requests to the repository's configured default branch.

## Natural next steps (not built, to keep this a focused project)

* **Transactional outbox** for the `order-created` publish instead of "save, then publish" — closes the small window where a database commit succeeds but the Kafka publish fails and the circuit-breaker's fallback only logs.

* **Saga compensation** — release reserved inventory when an order is cancelled after reservation, plus a scheduled reaper for orders that remain in `PENDING_INVENTORY` beyond a defined timeout.

* **Rate limiting** at the gateway, potentially using Resilience4j or Redis-backed limits per API key.

* **Distributed tracing** with OpenTelemetry to follow one `orderId` across all four services and Kafka hops in a single trace.
