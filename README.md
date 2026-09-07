# 🔗 Minima-AI

**A production-grade URL shortener built as a Java 25 / Spring Boot 4.1 microservices platform with a built-in AI agent you can just *talk* to, and an MCP server so Claude can manage your links directly.**

> Most URL shortener projects are a single Spring Boot app with one table.
> This one is six independently deployable services with service discovery, circuit breakers, a full LGTM observability stack (metrics + logs + distributed traces), an LLM-powered ReAct agent that can shorten, inspect, analyze, and delete your links through plain English, and a native MCP server so Claude Desktop can do the same - all spun up with a single `docker compose up`.

⭐ **If this saves you a weekend of wiring microservices together, a star helps a lot and tells me to keep building.**

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)](#)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-ReAct%20Agent-6DB33F?style=flat-square&logo=spring&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Server-7C3AED?style=flat-square)](#)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger%20UI-85EA2D?style=flat-square&logo=swagger&logoColor=black)](#)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Eureka%20%2B%20Gateway-6DB33F?style=flat-square&logo=spring&logoColor=white)](#)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-FF6B00?style=flat-square)](#)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](#)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)](#)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-event--driven-231F20?style=flat-square&logo=apachekafka&logoColor=white)](#)
[![Prometheus](https://img.shields.io/badge/Prometheus-metrics-E6522C?style=flat-square&logo=prometheus&logoColor=white)](#)
[![Grafana](https://img.shields.io/badge/Grafana-dashboards-F46800?style=flat-square&logo=grafana&logoColor=white)](#)
[![Tempo](https://img.shields.io/badge/Tempo-tracing-FF8800?style=flat-square)](#)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](#)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

---

## 📑 Table of Contents

- [Load Test Results](#-load-test-results)
- [Talk to Your Links (AI Agent)](#-talk-to-your-links)
- [Use it from Claude Desktop (MCP)](#-use-it-from-claude-desktop-mcp)
- [Architecture](#️-architecture)
- [One Command, Full Stack](#-one-command-full-stack)
- [Key Features](#-key-features)
- [Observability](#-observability)
- [Resilience](#️-resilience)
- [Tech Stack](#️-tech-stack)
- [Services at a Glance](#-services-at-a-glance)
- [Project Structure](#-project-structure)
- [Project Status](#️-project-status)
- [What's Next](#-whats-next)
- [Engineering Highlights](#-engineering-highlights)
- [License](#-license)

---

## ⚡ Load Test Results

Benchmark on the redirect hot path (`GET /r/{slug}`) — cache-aside Redis, single local machine running all 17 Docker containers simultaneously. Tested with [k6](https://k6.io), ramping to 200 concurrent virtual users over a realistic 3-stage load profile.

| Metric | Result |
|---|---|
| Total requests | **167,966** |
| Failed requests | **0 (0.00%)** |
| Checks passed | **334,932 / 334,932 (100%)** |
| Throughput | **1,324.7 req/s** |
| Avg latency | **6.91 ms** |
| p90 latency | 12.35 ms |
| **p95 latency** | **16.84 ms** |
| **p99 latency** | **34.6 ms** |
| Max latency | 157.55 ms |
| Peak concurrent VUs | 200 |

All k6 thresholds passed: `checks rate > 0.999`, `p(95) < 80ms`, `p(99) < 150ms`, `http_req_failed < 0.001`.

**Zero failures across ~168k requests, sub-17ms p95 under 200 concurrent users.** Redis cache-aside on the redirect path is doing exactly what it's supposed to.

<details>
<summary><strong>Raw k6 output</strong> (click to expand)</summary>

```text

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: loadtest.js
        output: -

     scenarios: (100.00%) 1 scenario, 200 max VUs, 2m30s max duration (incl. graceful stop):
              * realistic_load: Up to 200 looping VUs for 2m0s over 3 stages (gracefulRampDown: 30s, gracefulStop: 30s)

INFO[0006] Seeded 500 slugs                              source=console


  █ THRESHOLDS

    checks
    ✓ 'rate>0.999' rate=100.00%

    http_req_duration
    ✓ 'p(95)<80' p(95)=16.84ms
    ✓ 'p(99)<150' p(99)=34.6ms

    http_req_failed
    ✓ 'rate<0.001' rate=0.00%


  █ TOTAL RESULTS

    checks_total.......: 334932  2641.508245/s
    checks_succeeded...: 100.00% 334932 out of 334932
    checks_failed......: 0.00%   0 out of 334932

    ✓ status is 302
    ✓ has Location header

    HTTP
    http_req_duration..............: avg=6.91ms   min=530.8µs  med=4.98ms   p(90)=12.35ms  p(95)=16.84ms  p(99)=34.6ms  max=157.55ms
      { expected_response:true }...: avg=6.91ms   min=530.8µs  med=4.98ms   p(90)=12.35ms  p(95)=16.84ms  p(99)=34.6ms  max=157.55ms
    http_req_failed................: 0.00%  0 out of 167966
    http_reqs......................: 167966 1324.697472/s

    EXECUTION
    iteration_duration.............: avg=107.53ms min=100.71ms med=105.57ms p(90)=113.03ms p(95)=117.71ms p(99)=136.4ms max=262.21ms
    iterations.....................: 167466 1320.754122/s
    vus............................: 6      min=0           max=200
    vus_max........................: 200    min=200         max=200

    NETWORK
    data_received..................: 48 MB  376 kB/s
    data_sent......................: 23 MB  179 kB/s


running (2m06.8s), 000/200 VUs, 167466 complete and 0 interrupted iterations
realistic_load ✓ [======================================] 000/200 VUs  2m0s
```

</details>

---

## 🤖 Talk to your links

Minima AI's standout feature is `ai-service` - a [Spring AI](https://spring.io/projects/spring-ai) ReAct agent that turns plain-English requests into real actions across the platform.

```
POST /api/v1/ai/agent
{
  "message": "Shorten https://www.github.com and tell me how many clicks it has so far"
}
```

```json
{
  "reply": "The shortened URL for https://www.github.com is http://localhost:8082/r/G and it currently has 0 clicks."
}
```

Under the hood, the agent reasons step-by-step: it calls a `shortenUrl` tool against `url-service`, gets back a real `urlId`, then chains into `getUrlStats` against `analytics-service`, all without the LLM ever touching a database directly, and without the user ever knowing which microservice did what.

Try also:
- *"What are my top 3 most clicked links?"*
- *"Delete the URL with slug ABC123, I confirm it"*
- *"Is this URL safe: http://verify-paypal-login.xyz"*
- *"What's the latest news about the company behind this URL?"* - the agent can reach for live web search (Tavily) when its own knowledge isn't enough, not just internal tools

**Resilience built in:** if `url-service` or `analytics-service` is down or slow, the agent doesn't crash. Resilience4j circuit breakers trip and the agent replies conversationally:

```json
{
  "reply": "URL shortening is temporarily unavailable. Please try again in a moment."
}
```

> The underlying LLM is swapped via a single environment variable (`GROQ_MODEL`) rather than hardcoded - Groq's available model lineup changes often, so the agent's model choice is a config concern, not a code concern.

---

## 🔌 Use it from Claude Desktop (MCP)

`ai-service` doubles as a native **[MCP](https://modelcontextprotocol.io) server**. Point Claude Desktop at it and manage your shortened URLs without leaving the chat window.

**1. Generate an API key** (one-time, via auth-service):

```
POST /api/v1/auth/apikeys
Authorization: Bearer <your JWT>
{ "name": "Claude Desktop" }
```

You'll get back a `sk_...` key - copy it immediately, it's shown exactly once.

**2. Wire it into Claude Desktop's config:**

```jsonc
{
  "mcpServers": {
    "minima-ai": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8080/mcp",
        "--header", "X-MCP-Key: sk_your_key_here"
      ]
    }
  }
}
```

> On Windows, wrap the command as `"cmd"` / `["/c", "npx", ...]`.

**3. Tools exposed to Claude:**

| Tool                | What it does                                                        |
|---------------------|-----------------------------------------------------------------------|
| `mcp_shortenUrl`    | Shorten a long URL, return the short link + numeric ID                |
| `mcp_getUrlDetails` | Look up a URL's original destination + click count by slug            |
| `mcp_deleteUrl`     | Permanently delete a shortened URL (protocol-level confirmation)      |
| `mcp_getUrlStats`   | Click count for a specific URL by ID                                  |
| `mcp_getTopUrls`    | Your top-performing links by click count                              |

Auth is API-key based (SHA-256 hashed, validated against Redis on every call) rather than JWT. MCP connections are long-lived and tokens shouldn't expire mid-session. Every tool call is circuit-breaker protected against the same `url-service`/`analytics-service` dependencies the chat agent uses.

---

## 🏗️ Architecture

```mermaid
graph TB
    Client[("Client")]
    Eureka{{"eureka-server :8761<br/>Service Registry"}}
    Gateway["api-gateway :8080<br/>JWT • Rate limiting • Circuit breakers • Routing"]

    subgraph Services
        Auth["auth-service :8081<br/>JWT + OAuth2 + Refresh tokens + Password reset"]
        Url["url-service :8082<br/>Shortening • Base62 • Redirects"]
        Analytics["analytics-service :8083<br/>Click tracking • Bloom filter"]
        AI["ai-service :8084<br/>ReAct agent • MCP server • Classification • Safety"]
    end

    PG1[("Postgres<br/>minima_ai_auth")]
    PG2[("Postgres<br/>minima_ai_urls")]
    PG3[("Postgres<br/>minima_ai_analytics")]
    RedisDB[("Redis 7<br/>cache • rate limit • bloom filter • API keys")]
    Kafka{{"Apache Kafka"}}
    Obs["Prometheus + Grafana<br/>metrics & dashboards"]
    MCP[("Claude Desktop<br/>via MCP")]

    Client --> Gateway
    MCP -. "X-MCP-Key" .-> AI

    Gateway -. discovers .-> Eureka
    Auth -. registers .-> Eureka
    Url -. registers .-> Eureka
    Analytics -. registers .-> Eureka
    AI -. registers .-> Eureka

    Gateway -- "circuit breaker" --> Auth
    Gateway -- "circuit breaker" --> Url
    Gateway -- "circuit breaker" --> Analytics
    Gateway -- "circuit breaker" --> AI

    Auth --> PG1
    Auth --> RedisDB

    Url --> PG2
    Url --> RedisDB
    Url -- "url.created / url.clicks / url.deleted" --> Kafka

    Kafka --> Analytics
    Analytics --> PG3
    Analytics --> RedisDB

    Kafka --> AI
    AI -- "url.classified" --> Kafka
    Kafka -.-> Url
    AI -- "circuit breaker + retry" --> Url
    AI -- "circuit breaker + retry" --> Analytics

    Auth -.->|/actuator/prometheus| Obs
    Url -.->|/actuator/prometheus| Obs
    Analytics -.->|/actuator/prometheus| Obs
    AI -.->|/actuator/prometheus| Obs
    Gateway -.->|/actuator/prometheus| Obs
```

**Event flow example:** shortening a URL triggers `url.created` → consumed by both `analytics-service` (initializes click counters) and `ai-service` (classifies the URL via LLM, generates a title, runs a safety check) → `ai-service` publishes `url.classified` → consumed back by `url-service` to persist the AI-generated title/category/safety flag. Fully async, fully decoupled - a real SAGA choreography, not a hardcoded call chain.

<details>
<summary><strong>Observability pipeline</strong> (traces + logs + metrics, click to expand)</summary>

```mermaid
graph LR
    Svc["All 6 services"]
    Otel[["OTel Collector"]]
    Tempo[("Tempo<br/>traces")]
    Loki[("Loki<br/>logs")]
    Prom[("Prometheus<br/>metrics")]
    Promtail["Promtail"]
    Grafana["Grafana<br/>dashboards + explore"]

    Svc -- "OTLP spans" --> Otel --> Tempo --> Grafana
    Svc -- "JSON logs" --> Promtail --> Loki --> Grafana
    Svc -- "/actuator/prometheus scrape" --> Prom --> Grafana
```

Every span is tagged with a trace ID that also lands in the structured JSON logs (Logback MDC), so a slow or failing request can be followed from a Grafana metric spike straight into the exact trace and the exact log lines across every service it touched - no manual correlation.

</details>

---

## 🚀 One command, full stack

```bash
git clone https://github.com/Mrillogical/minima-ai.git
cd minima-ai
cp .env.example .env
# fill in: DB credentials, Redis password, JWT secret, Groq API key, mail credentials

docker compose up -d --build
```

That's it, **17 containers**, fully wired:

| What                                    | URL                                       |
|-----------------------------------------|--------------------------------------------|
| API Gateway (entry point)               | http://localhost:8080                       |
| **Swagger UI (all services, one page)** | http://localhost:8080/swagger-ui.html       |
| Eureka dashboard                        | http://localhost:8761                       |
| Grafana (dashboards + tracing explore)  | http://localhost:3000 (`admin` / `admin`)   |
| Prometheus                              | http://localhost:9090                       |
| Kafka UI                                | http://localhost:8090                       |

Loki and Tempo don't need direct browsing - they're auto-provisioned as Grafana datasources, so logs and traces are queried straight from the Grafana Explore tab.

All 6 services build from multi-stage Dockerfiles (`eclipse-temurin:25-jdk` → `eclipse-temurin:25-jre`), register with Eureka on startup, and expose `/actuator/prometheus` for metrics scraping out of the box.

**Try it in 10 seconds once it's up:**

```bash
# Register + login (or use OAuth2 Google)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"your_password"}'

# Shorten a URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Authorization: Bearer <your JWT>" \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://github.com/Mrillogical/minima-ai"}'

# Follow the redirect
curl -L http://localhost:8082/r/<slug>
```

---

## 📘 API Docs (Swagger / OpenAPI)

Every service ships full OpenAPI 3.1 docs via [springdoc-openapi](https://springdoc.org/), aggregated into a single Swagger UI at the gateway:

| Service                                | Swagger UI                              |
|-----------------------------------------|-------------------------------------------|
| **Gateway - aggregated, all services** | http://localhost:8080/swagger-ui.html     |
| Auth Service                            | http://localhost:8081/swagger-ui.html     |
| URL Service                             | http://localhost:8082/swagger-ui.html     |
| Analytics Service                       | http://localhost:8083/swagger-ui.html     |
| AI Service                              | http://localhost:8084/swagger-ui.html     |


---

## ✨ Key Features

| Category              | What's implemented                                                                                                                                                                                    |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **AI / LLM**            | ReAct agent (Spring AI + tool calling), live web search (Tavily) for up-to-date info the LLM's own knowledge can't cover, AI URL classification (title, category, safety), AI slug suggestions, AI-generated analytics summaries, persistent conversation memory (Postgres-backed)      |
| **MCP**                 | Native MCP server (`STREAMABLE` transport) exposing URL/analytics tools to Claude Desktop, hashed API-key auth, circuit-breaker-protected tool calls                                                  |
| **API Docs**            | OpenAPI 3.1 on every service via springdoc, aggregated single Swagger UI at the gateway                                                                                                                |
| **Auth & Security**     | JWT access/refresh tokens, OAuth2 Google login, BCrypt password hashing, email verification, **forgot/reset password flow** (revokes all active sessions on reset), audit logging, header-based service-to-service auth enforced independently on every service |
| **URL Shortening**      | Base62 encoding, custom slugs, expiry dates, cache-aside Redis caching - **1,325 req/s at ~7 ms avg / 16.84 ms p95 on a single instance**                                                             |
| **Analytics**           | Real-time click counters (Redis), hourly rollups, Bloom-filter click deduplication, per-user top-URLs leaderboard                                                                                      |
| **Service Discovery**   | Netflix Eureka - all 5 business services self-register; gateway routes via `lb://` for dynamic load balancing                                                                                         |
| **Resilience**          | Resilience4j circuit breakers + retries on every cross-service call, with custom fallbacks; transactional outbox + ShedLock-scheduled retry for failed Kafka publishes                                |
| **Distributed Jobs**    | ShedLock-coordinated scheduled jobs (expiry cleanup, cache warming, DLQ retry, token cleanup) - safe across multiple instances                                                                          |
| **Gateway**             | Spring Cloud Gateway (WebFlux) - central JWT validation, Redis token-bucket rate limiting, per-route circuit breakers, CORS, trace ID propagation                                                      |
| **Observability**       | Full LGTM stack - Prometheus metrics, Loki logs, Tempo distributed traces, OpenTelemetry export from every service, auto-provisioned Grafana dashboards, MDC trace-ID correlation across logs and spans |
| **CI**                  | GitHub Actions - full multi-module Maven build + unit test suite on every push and PR, failure artifacts uploaded automatically                                                                       |
| **Modern Java**         | Java 25, virtual threads, records for all DTOs/events, sealed types, text blocks for SQL/prompts                                                                                                       |

---

## 📊 Observability

Every service exposes a `/actuator/prometheus` endpoint, ships structured JSON logs to Loki via Promtail, and exports OpenTelemetry traces through a shared OTel Collector into Tempo. Grafana comes auto-provisioned with `docker compose up` - metrics, logs, and traces are all queryable from one place, correlated by trace ID.

The included Grafana dashboard covers:

1. **HTTP request rate** - requests processed per second across all services.
2. **95th percentile latency (P95)** - response time distribution under load.
3. **JVM heap usage** - real-time heap memory utilization.
4. **CPU usage** - CPU consumption for each service instance.
5. **Service uptime** - application uptime for all running services.
6. **4xx and 5xx error rates** - client and server errors tracked independently.
7. **Live thread count** - active JVM threads per service.
8. **GC pause time** - garbage collection pause duration.
9. **HikariCP active connections** - active database connection pool usage.

Beyond dashboards, Grafana's **Explore** tab lets you jump from a metric spike straight into the exact distributed trace (Tempo) and the exact log lines across every service it touched (Loki) - no manual timestamp-matching across three separate tools.

![](docs/grafana.png)

---

## 🛡️ Resilience

Two layers of circuit breakers, both Resilience4j, both Spring Boot 4 native:

- **`ai-service` → `url-service` / `analytics-service`** - `@CircuitBreaker` + `@Retry` + `@TimeLimiter` on `CompletableFuture`-returning ops methods, backed by explicit `readTimeout` on the underlying RestClient (shorter than the TimeLimiter window) so cancellation is real, not just cosmetic. 4xx responses pass through untouched; connection failures and 5xx trip the breaker and trigger a friendly fallback the agent relays in plain English.
- **`api-gateway` → all 4 downstream services** - declarative `CircuitBreaker` route filters per service, with per-service-tuned thresholds (LLM-backed `ai-service` gets longer slow-call/timeout windows than CRUD services) and a dedicated `FallbackController` returning structured `503` JSON instead of hangs or raw stack traces.

A recurring correctness pattern enforced across every service: any Redis write or delete that happens inside a `@Transactional` method is deferred to `TransactionSynchronizationManager.afterCommit()`, never fired eagerly mid-transaction. If the database transaction rolls back after an eager Redis write, the cache silently drifts from the source of truth with no error raised - the kind of bug that only shows up under real concurrent load, not in a manual test.

---

## 🛠️ Tech Stack

| Layer                  | Technology                                                                          |
|--------------------------|--------------------------------------------------------------------------------------|
| Language / Runtime      | Java 25 (virtual threads enabled)                                                     |
| Framework               | Spring Boot 4.1, Spring Cloud Gateway, Spring Security 7                              |
| Service Discovery       | Netflix Eureka (Spring Cloud)                                                         |
| AI                      | Spring AI 2.0, ReAct tool-calling agent, MCP server, Groq (pluggable model via env var), Tavily (web search) |
| API Docs                | springdoc-openapi 3 (OpenAPI 3.1, Swagger UI, gateway-aggregated)                     |
| Database                | PostgreSQL 16 + Liquibase migrations (DB-per-service)                                |
| Cache / Rate Limiting   | Redis 7 (RedisBloom module)                                                           |
| Messaging               | Apache Kafka                                                                          |
| Build                   | Maven (multi-module)                                                                  |
| Containerization        | Docker + Docker Compose (17-container stack)                                         |
| Resilience              | Resilience4j (`resilience4j-spring-boot4`, Spring Cloud Circuit Breaker), ShedLock    |
| Logging                 | SLF4J + Logback + Logstash JSON encoder, Loki + Promtail                             |
| Metrics                 | Micrometer + Prometheus + Grafana                                                     |
| Distributed Tracing     | Micrometer Tracing + OpenTelemetry Collector + Tempo                                 |
| CI                      | GitHub Actions (multi-module Maven build + tests on every push/PR)                    |
| Load Testing            | k6                                                                                    |

---

## 📡 Services at a Glance

| Service              | Port | Responsibility                                                                                              |
|-----------------------|------|----------------------------------------------------------------------------------------------------------------|
| `eureka-server`       | 8761 | Service registry - all 5 services below register here                                                        |
| `api-gateway`         | 8080 | Single entry point - JWT validation, rate limiting, circuit breakers, routing, CORS, aggregated Swagger UI    |
| `auth-service`        | 8081 | Registration, login, JWT/refresh tokens, OAuth2 Google, email verification, forgot/reset password, MCP API keys |
| `url-service`         | 8082 | URL shortening, Base62 slugs, redirects, cache-aside Redis, Kafka event publishing                            |
| `analytics-service`   | 8083 | Kafka consumer for click events, Bloom-filter dedup, real-time + hourly analytics                             |
| `ai-service`          | 8084 | ReAct agent, MCP server, AI URL classification, slug suggestions, safety checks, summaries                    |

---

## 📂 Project Structure

```
Minima-AI/
├── docker-compose.yml          # full 17-container stack
├── observability/
│   ├── prometheus/
│   ├── grafana/provisioning/   # auto-provisioned datasources + dashboard
│   ├── loki/
│   ├── promtail/
│   ├── otel-collector/
│   └── tempo/
├── eureka-server/
├── api-gateway/                 # routing, auth, rate limiting, circuit breakers, Swagger aggregation
├── auth-service/
│   └── src/main/java/com/shortlyai/auth/
│       ├── authentication/       # login, register, refresh, logout, verify
│       ├── password/             # forgot/reset password
│       ├── apikey/                # MCP API key generation + revocation
│       └── token/                 # refresh token lifecycle
├── url-service/
│   └── src/main/java/com/shortlyai/url/
│       ├── shortening/           # Base62, core CRUD
│       ├── redirect/              # Public redirect endpoint
│       ├── expiry/                # Scheduled cleanup
│       ├── consumer/              # Consumes AI classification results
│       ├── dlq/                    # Dead-letter-queue retry
│       └── events/                  # Kafka event records
├── analytics-service/           # Click tracking, Bloom filter, rollups
└── ai-service/
    └── src/main/java/com/shortlyai/ai/
        ├── agent/                # ChatClient + @Tool methods (circuit-breaker protected)
        ├── mcp/                    # MCP server tools + API key auth filter
        ├── classification/        # AI title/category/safety pipeline
        ├── slug/                    # AI slug suggestions
        └── summary/                  # AI-generated analytics summaries
```

Every service follows **feature-based packaging** - each feature folder contains its own controller, service, repository, and DTOs. No layer-based `controllers/`, `services/`, `repositories/` folders.

---

## 🗺️ Project Status

- [x] `eureka-server` - service discovery for all 5 business services
- [x] `auth-service` - JWT, OAuth2 Google, refresh tokens, forgot/reset password, audit logging, MCP API keys
- [x] `url-service` - shortening, redirects, cache-aside, Kafka events, DLQ retry
- [x] `analytics-service` - click tracking, Bloom filter dedup, hourly rollups, per-user leaderboard
- [x] `api-gateway` - JWT validation, rate limiting, routing, CORS, circuit breakers, aggregated Swagger UI
- [x] `ai-service` - ReAct agent, AI classification pipeline, slug/safety/summary endpoints, persistent chat memory
- [x] MCP server - tested end-to-end with Claude Desktop via `mcp-remote`
- [x] OpenAPI / Swagger UI - every service, aggregated at the gateway
- [x] Full Docker containerization - 17 containers, single `docker compose up`
- [x] Observability - Prometheus + Loki + Tempo + OTel Collector, auto-provisioned Grafana
- [x] Resilience4j circuit breakers - gateway-level + AI-agent-level + MCP-level, with fallbacks
- [x] CI pipeline - GitHub Actions, full build + test suite on every push/PR
- [x] Load tested - 1,325 req/s at ~7 ms avg / 16.84 ms p95, 0 errors across ~168k requests, single instance

---

## 🔭 What's Next

<details>
<summary><strong>Known gaps and planned work</strong> (click to expand)</summary>

- **Test coverage** - the biggest gap right now. Unit tests exist across all services, but Kafka producer/consumer flows and full Spring context boot tests aren't covered in CI yet. Every cross-service schema/shape bug this project has surfaced so far has lived exactly there.
- **JWT startup validation** - a prod-profile guard against placeholder/weak JWT secrets is designed but not yet wired in.
- **Actuator hardening** - `/actuator/**` is currently open (`permitAll`) on every service; fine behind a private network, needs restricting before any public deployment.
- **Image publishing & deployment** - CI currently runs build + tests only. GHCR image publishing and a managed deployment (Oracle Cloud + Neon Postgres + Upstash Redis/Kafka) are planned but not live yet.
- **Contract testing** - would catch cross-service field-name/shape drift (like the Kafka mismatches this project has already found and fixed) earlier than an end-to-end test would.

</details>

---

## 🧠 Engineering Highlights

- **Event-driven SAGA choreography** - URL creation triggers a chain of independent Kafka consumers (analytics initialization, AI classification, result persistence) with no central orchestrator. A real choreography pattern, not a distributed monolith.

- **TimeLimiter done correctly** - `@TimeLimiter` wraps `CompletableFuture`- returning ops methods (the only way the aspect actually fires). The underlying RestClient has an explicit `readTimeout` shorter than the TimeLimiter window because `CompletableFuture.cancel()` per JDK Javadoc does not interrupt the running task. Without a real socket timeout, "cancellation" is cosmetic. Both layers are present.

- **Redis-after-commit discipline, enforced everywhere** - every Redis mutation inside a `@Transactional` method is deferred to `afterCommit()`, not fired eagerly. Caught and fixed as a recurring bug class across multiple services during code review - the kind of correctness gap that only manifests under a rolled-back transaction, easy to miss, easy to ship silently.

- **Cross-service contract discipline** - every Kafka event and REST DTO is a Java record. Field-name mismatches across service boundaries are a real class of bug this project surfaced, debugged, and enforced against.

- **AI as tool-calling orchestrator, not a black box** - the LLM never touches infrastructure directly. It calls typed `@Tool` methods that hit real microservices. `ToolContext` (chat agent) and a `ThreadLocal`-scoped security context (MCP) keep user identity completely out of the LLM's input/output.

- **Defense-in-depth auth** - gateway validates JWTs once, but every downstream service independently validates the `X-User-Id` header it receives. No service is an open door even if reached directly, bypassing the gateway.

- **Guaranteed delivery without a managed DLQ** - failed Kafka publishes are persisted to a Postgres `failed_events` table and retried on a ShedLock-coordinated schedule, surviving broker outages without requiring a managed Kafka cluster with DLQ topic support.

- **Real load numbers** - ~168,000 requests at peak, 0 failures, benchmarked with k6. The redirect hot path sustains 1,325 req/s at ~7 ms average latency and 16.84 ms p95 on a single local instance under 200 concurrent users - all k6 thresholds green.

---

## 👤 Author

**Kunal Singh Yadav** — Java Backend Developer
Building production-grade systems, not tutorial apps.

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-blue?logo=linkedin)](https://www.linkedin.com/in/kunal-singh-yadav)
[![GitHub](https://img.shields.io/badge/GitHub-Follow-black?logo=github)](https://github.com/Mrillogical)
[![Email](https://img.shields.io/badge/Email-Contact-red?logo=gmail)](mailto:kunalsy24@gmail.com)

---

## 📄 License

MIT — built for portfolio and learning purposes.
⭐ **Found this useful, interesting, or just well over-engineered for a URL shortener? Star the repo, it genuinely helps and costs you two seconds.**

