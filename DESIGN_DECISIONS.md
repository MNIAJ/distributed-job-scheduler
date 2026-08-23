# Design Decisions

## 1. PostgreSQL over MySQL

**Decision:** Use PostgreSQL as the primary database.

**Reason:** This assignment is fundamentally about reliable concurrent job
execution. PostgreSQL's `FOR UPDATE SKIP LOCKED` provides native row-level
locking that makes atomic job claiming trivial to implement correctly.

MySQL supports `FOR UPDATE` but `SKIP LOCKED` was added later and behaves
differently. PostgreSQL's implementation is more mature and predictable for
this exact use case.

**Tradeoff:** Slightly more setup complexity vs MySQL familiarity.
Worth it for correctness guarantees.

---

## 2. Database Polling over Message Queues

**Decision:** Workers poll PostgreSQL every 5 seconds instead of using
Kafka, RabbitMQ, or Redis queues.

**Reason:**
- Zero additional infrastructure — no separate message broker to deploy
- Full observability — every job's state is always visible in the DB
- Simpler failure recovery — stuck jobs are just rows with a status field
- Transactional claiming — job claim and status update happen atomically

**Tradeoff:** Higher database load at scale vs push-based systems.
At millions of jobs/second, a message queue would be more efficient.
At this scale, polling is simpler and more observable.

**Industry precedent:** Sidekiq (Ruby), Delayed::Job, and many production
systems use this exact pattern successfully.

---

## 3. Retry Policy on Queue, Not a Separate Table

**Decision:** Store max_retries, retry_type, and base_delay_seconds
directly on the Queue entity.

**Reason:** All jobs in a queue share the same retry behavior by design.
A separate RetryPolicy table would add a join to every job claim query
without adding real flexibility — you'd still need one policy per queue.

**Tradeoff:** Less flexible if you later need per-job retry policies.
Acceptable for this use case where queue-level configuration is the right
abstraction.

---

## 4. Simulating Multiple Workers in One JVM

**Decision:** Run 3 worker threads inside the same Spring Boot application
instead of deploying separate worker services.

**Reason:** The concurrency problem is identical whether workers are in
one JVM or across 10 machines — the database is the coordination point.
`FOR UPDATE SKIP LOCKED` works the same way in both cases.

This approach demonstrates the distributed systems concept correctly while
keeping deployment simple for an assignment context.

**Tradeoff:** In production, workers would be separate deployable services
that scale independently. The architectural change needed: extract
WorkerService into its own Spring Boot app pointing to the same DB.

---

## 5. JWT over Sessions

**Decision:** Stateless JWT authentication instead of server-side sessions.

**Reason:** Workers and dashboard clients send programmatic API requests,
not browser-based sessions. JWT tokens work uniformly across all clients
without server-side session storage.

**Tradeoff:** Tokens cannot be invalidated before expiry without a
token blacklist. Acceptable for this use case — set short expiry (24h).

---

## 6. Single Responsibility: CustomUserDetailsService

**Decision:** Separate CustomUserDetailsService from AuthService instead
of implementing UserDetailsService directly in AuthService.

**Reason:** AuthService handles business logic (signup, login, token
generation). Spring Security's UserDetailsService contract is an
infrastructure concern. Mixing them creates a circular bean dependency:
SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig.

Separating them eliminates the cycle and gives each class one clear job.

---

## 7. Idempotency Keys on Jobs

**Decision:** Optional idempotency_key field with a unique constraint.

**Reason:** Clients may retry job submission requests on network timeout.
Without idempotency keys, this creates duplicate jobs. With the unique
constraint, the second submission returns the existing job silently.

**Tradeoff:** Clients must generate and track their own keys.
Worth it to prevent silent duplicate work.

---

## 8. Dead Letter Queue as a Separate Table

**Decision:** Move permanently failed jobs to a dead_letter_queue table
rather than leaving them in the jobs table with status=DEAD.

**Reason:**
- Keeps the jobs table clean for active work
- DLQ entries have additional metadata (failure_reason, moved_at)
- Enables separate monitoring and alerting for DLQ
- Manual retry from DLQ is a distinct operation from normal job retry

**Tradeoff:** Slightly more complex retry logic (must delete from DLQ
and reset the original job). Worth it for observability.