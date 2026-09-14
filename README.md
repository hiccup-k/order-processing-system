# Event-Driven Order Processing System

A small, production-shaped system for placing an order, checking/reserving inventory,
and notifying the customer of the outcome — built to demonstrate the same patterns used
in real event-driven microservice systems, without the sprawl of a system nobody will
actually finish or re-read.

## Architecture

```
                      ┌────────────────┐
   client ──JWT────▶  │  API Gateway   │  (Spring Cloud Gateway, reactive)
                      │  /auth/login   │  issues JWTs
                      └───────┬────────┘
                              │ routes + validates JWT at the edge
              ┌───────────────┴────────────────┐
              ▼                                 ▼
      ┌───────────────┐                ┌────────────────────┐
      │ Order Service │                │ Inventory Service   │
      │ (Postgres)    │                │ (Postgres)          │
      └───────┬───────┘                └──────────┬──────────┘
              │  order-created                     │
              ▼                                    │
      ┌────────────────────────────────────────────┴──────┐
      │                  Kafka + Schema Registry            │
      │   order-created · inventory-reserved ·              │
      │   inventory-failed · order-confirmed                │
      └───────┬───────────────────────────────────┬────────┘
              │ inventory-reserved / -failed        │ order-confirmed
              ▼                                     ▼
      ┌───────────────┐                   ┌────────────────────┐
      │ Order Service │                   │ Notification Service│
      │ (confirms/    │                   │ (logs / would call  │
      │  fails order) │                   │  email/SMS provider)│
      └───────────────┘                   └────────────────────┘
```

**4 services, on purpose** — API Gateway, Order Service, Inventory Service, Notification
Service. No separate User Service: the gateway owns JWT issuance against an in-memory
user store, which keeps the auth story coherent without a fifth service and its own DB.

### Event flow

1. `POST /api/v1/orders` → Order Service persists the order as `PENDING_INVENTORY` and
   publishes **`order-created`** (Avro, validated against Schema Registry), keyed by
   `orderId`.
2. Inventory Service consumes `order-created`, tries to reserve every line item in one
   DB transaction, and publishes either **`inventory-reserved`** or **`inventory-failed`**.
3. Order Service consumes both, moves the order to `CONFIRMED` or `FAILED`, and
   publishes **`order-confirmed`** (carrying the final status) keyed by `orderId`.
4. Notification Service consumes `order-confirmed` and dispatches a notification (stubbed
   as a log line — swap in a real email/SMS/push client without touching the listener).

All four topics key events by `orderId`, so every event for a given order lands on the
same partition in each topic and is processed in order relative to itself — without
needing a global ordering guarantee across unrelated orders.

## Why these design choices (interview talking points)

- **Kafka + Avro + Schema Registry.** Every event is a generated Avro class
  (`common-avro` module), not a hand-maintained JSON DTO. Schema Registry enforces
  backward compatibility on every publish, so a producer can't ship a breaking schema
  change without consumers finding out at deploy time instead of at 2am.
- **Consumer groups.** `order-service`, `inventory-service`, and `notification-service`
  are three independent consumer groups reading overlapping topics. Each group tracks
  its own offsets, so notification-service doesn't "steal" messages inventory-service
  needs — they each get a full, independent copy of the stream.
- **Partitions & ordering.** Kafka only guarantees order *within* a partition. Keying
  every event by `orderId` is what gives us "all events for order #123 are processed in
  the order they were published" without needing a single-partition topic (which would
  kill throughput).
- **Idempotency.** At-least-once delivery means every consumer will eventually see a
  redelivered message (consumer crash before offset commit, rebalance, etc.). Two
  different idempotency techniques are used deliberately, to show both:
  - **Inventory Service** keeps a `processed_order_events` table and checks/records the
    `orderId` inside the *same transaction* as the stock decrement — a textbook
    exactly-once-effect pattern over an at-least-once transport.
  - **Order Service** relies on the aggregate's own state machine: `confirmOrder` /
    `failOrder` are no-ops unless the order is still `PENDING_INVENTORY`, so a
    redelivered `inventory-reserved` after the order is already `CONFIRMED` is silently
    ignored.
- **Offsets & delivery semantics.** `ack-mode: RECORD` commits the offset only after the
  listener method returns normally, giving at-least-once delivery (not at-most-once) —
  the trade-off being possible redelivery, which is exactly what the idempotency work
  above exists to handle.
- **Resilience.** Kafka publishes go through Resilience4j `@Retry` (exponential backoff)
  wrapped in a `@CircuitBreaker`, with a fallback that logs (a real system would write to
  an outbox table here for later replay instead of just logging).
- **Concurrency control.** `Order` uses JPA optimistic locking (`@Version`) since a
  REST-triggered cancel and a Kafka-triggered confirm could race. `InventoryItem` uses a
  **pessimistic** row lock (`SELECT ... FOR UPDATE`) instead, because oversell is a
  correctness bug, not just a UI annoyance, and pessimistic locking avoids the
  retry-loop complexity optimistic locking would need under real contention.
- **Defense in depth on auth.** The gateway validates the JWT and rejects bad requests
  at the edge, but Order Service and Inventory Service *also* independently validate
  every JWT — a header set by the gateway is never trusted blindly by a downstream
  service, in case that service is ever reachable directly inside the cluster network.

## Tech stack

Java 21 · Spring Boot 3.3 · Spring Cloud Gateway (reactive) · Spring Security + JWT
(JJWT) · Spring Data JPA/Hibernate · Kafka + Avro + Confluent Schema Registry ·
PostgreSQL · Flyway · Resilience4j (retry + circuit breaker) · Docker + Docker Compose ·
JUnit 5 + Mockito + AssertJ + Testcontainers · Maven (multi-module reactor) ·
GitHub Actions

## Repository layout

```
order-processing-system/
├── common-avro/          Avro schemas, shared by every service via avro-maven-plugin
├── order-service/         REST + JPA + Kafka producer/consumer + JWT verification
├── inventory-service/     Kafka consumer/producer + JPA + JWT verification
├── notification-service/  Kafka consumer only, no DB
├── api-gateway/           Reactive gateway: JWT issuance + routing + edge auth
├── docker-compose.yml     Kafka, Zookeeper, Schema Registry, 2x Postgres, all 4 services
└── .github/workflows/ci.yml
```

## Running it locally

```bash
# 1. Build & install the shared Avro module first (everything else depends on it)
mvn -pl common-avro -am install -DskipTests

# 2. Bring the whole stack up
docker compose up --build
```

Services come up on:

| Service              | Port |
|----------------------|------|
| api-gateway           | 8080 |
| order-service         | 8081 |
| inventory-service     | 8082 |
| notification-service  | 8083 |
| schema-registry       | 8085 |
| kafka-ui              | 8090 |
| kafka (host access)   | 9092 |

Inventory Service is seeded (via Flyway) with demo stock for `SKU-1`, `SKU-2`, `SKU-3`.

### Try the happy path

```bash
# Get a token (demo users: alice/password123, admin/adminpass123)
TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | jq -r .accessToken)

# Create an order
curl -s -X POST localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","items":[{"sku":"SKU-1","quantity":2,"price":10.00}]}'

# Watch it move CREATED -> PENDING_INVENTORY -> CONFIRMED
curl -s localhost:8080/api/v1/orders/1 -H "Authorization: Bearer $TOKEN"
```

Request more than available stock (e.g. 999 of `SKU-3`, seeded at 5) to see the
`inventory-failed` → order `FAILED` path, and check notification-service's logs
(`docker compose logs -f notification-service`) either way.

## Running tests

```bash
mvn -pl common-avro -am install -DskipTests
mvn -pl order-service -am test          # Mockito unit tests + MockMvc slice + Testcontainers Postgres IT
mvn -pl inventory-service -am test      # Mockito unit tests for reservation atomicity/idempotency
mvn -pl notification-service -am test
mvn -pl api-gateway -am test            # JwtUtil + WebTestClient auth flow tests
```

CI (`.github/workflows/ci.yml`) runs the same sequence, then builds every service's
Docker image, on every push/PR to `main`.

## Natural next steps (not built, to keep this from becoming an 8-service project)

- **Transactional outbox** for the order-created publish, instead of "save, then
  publish" — closes the small window where a DB commit succeeds but the publish fails
  and the circuit breaker's fallback only logs.
- **Saga compensation**: releasing reserved inventory when an order is cancelled after
  reservation, and a scheduled reaper for orders stuck in `PENDING_INVENTORY` past a
  timeout (Kafka doesn't guarantee delivery time, only order-within-partition).
- **Rate limiting** at the gateway (Resilience4j or Redis-backed) per API key.
- **Distributed tracing** (OpenTelemetry) to follow one `orderId` across all four
  services and Kafka hops in one trace view.
