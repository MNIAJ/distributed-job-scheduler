# Design Decisions

Eight major trade-offs made during the design of this system, with the reasoning behind each.

---

## 1. PostgreSQL over MySQL

**Decision:** PostgreSQL as the primary database.

**Reason:** The core challenge of this assignment is reliable concurrent job execution. PostgreSQL's `FOR UPDATE SKIP LOCKED` provides native row-level locking that makes atomic job claiming a single SQL statement. MySQL added `SKIP LOCKED` later and its behaviour is less predictable for this exact pattern. The decision was PostgreSQL-first from day one — not as a preference but as the right tool for the concurrency problem.

**Trade-off:** Slightly more setup than MySQL. Worth it for correctness guarantees.

---

## 2. Database polling over message queues

**Decision:** Workers poll PostgreSQL every 5 seconds rather than using Kafka, RabbitMQ, or Redis.

**Reasons:**
- Zero additional infrastructure — no message broker to deploy, monitor, or operate
- Full observability — every job's state is always a SQL query away
- Simpler failure recovery — stuck jobs are just rows with stale status
- Transactional claiming — job lock and status update happen in one atomic operation

**Trade-off:** Higher database load at very high scale. At millions of jobs per second, a push-based message queue wins on throughput. At this scale, polling is simpler, more observable, and more maintainable.

**Industry precedent:** Sidekiq, Delayed::Job, and Faktory all use this approach successfully in production.

---

## 3. RetryPolicy as a separate table

**Decision:** Retry configuration (`max_retries`, `retry_type`, `base_delay_seconds`) stored in a `retry_policies` table, not inline on the queue.

**Reason:** Retry policies are reusable configuration. Multiple queues may share the same retry behaviour. A separate table means you define the policy once and reference it — no duplication, and changing a policy updates all queues that use it.

**Trade-off:** One extra join on every queue read. Negligible at this scale, worth it for maintainability.

---

## 4. Workers as threads in one JVM, not separate services

**Decision:** Three worker threads inside the same Spring Boot application, not separate deployable services.

**Reason:** The concurrency guarantee comes from the database locking, not from network topology. Whether workers are threads in one JVM or processes across ten machines, `FOR UPDATE SKIP LOCKED` works identically — the database is the coordination point.

This demonstrates the distributed systems concept correctly while keeping deployment simple.

**Production path:** To scale out, extract `WorkerService` into a separate Spring Boot app pointing at the same database. The locking logic is unchanged — you just have more pollers.

---

## 5. JWT over server-side sessions

**Decision:** Stateless JWT authentication.

**Reason:** Workers and the dashboard are programmatic API clients — they make HTTP requests with tokens, not browser sessions. JWT works uniformly across all clients without server-side session storage. Stateless also means the app scales horizontally without session affinity.

**Trade-off:** Tokens cannot be revoked before expiry without a blacklist. Mitigated by short expiry (24h) and the low-risk nature of the system.

---

## 6. CustomUserDetailsService separated from AuthService

**Decision:** Spring Security's `UserDetailsService` contract implemented in a dedicated `CustomUserDetailsService`, not mixed into `AuthService`.

**Reason:** `AuthService` handles business logic — signup, login, token generation. `CustomUserDetailsService` handles an infrastructure concern — loading users during JWT filter validation. Combining them creates a circular bean dependency: `SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig`. Separating them eliminates the cycle and gives each class one clear responsibility.

---

## 7. Idempotency keys on jobs

**Decision:** Optional `idempotency_key` field with a unique constraint on the `jobs` table.

**Reason:** API clients often retry requests on network timeout. Without idempotency keys, a retry creates a duplicate job. With the unique constraint, the second submission silently returns the existing job — the client gets the correct response and no duplicate work is executed.

**Trade-off:** Clients must generate and manage their own keys. Acceptable — the system can't generate meaningful idempotency keys without knowing the client's intent.

---

## 8. Dead Letter Queue as a separate table

**Decision:** Permanently failed jobs moved to a `dead_letter_queue` table rather than left in `jobs` with status DEAD.

**Reasons:**
- Keeps the `jobs` table focused on active work — no permanently dead rows clogging queries
- DLQ entries carry extra metadata: `failure_reason`, `moved_at`, `total_attempts`
- Enables separate monitoring, alerting, and manual retry workflows
- A job in the DLQ is a different concept from a job that failed — it deserves its own home

**Trade-off:** Manual retry requires deleting from DLQ and resetting the original job. A small amount of complexity for meaningful operational clarity.

---

## 9. Environment Variables over Hardcoded Configuration

**Decision:** Externalized configuration using `.env` and Docker environment variables.

**Reason:** Database credentials, JWT configuration, and server settings should not be hardcoded in the application. Environment variables make the project portable across local development, Docker, and production deployments without changing the source code.

**Trade-off:** Slightly more setup for first-time users, but significantly better security and deployment flexibility.