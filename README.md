# SmartURL — Distributed URL Shortener

A production-grade URL shortener built with **Java 17 + Spring Boot 3**, featuring Redis caching, Token Bucket rate limiting, async analytics, and JWT authentication.

>showcasing: Base62 encoding, LRU caching (Redis), Token Bucket DSA, Producer-Consumer concurrency pattern, REST API design, and Docker deployment.

---

## Architecture

```
Client → Rate Limiter (Token Bucket) → Controller
                                           │
                              ┌────────────┴────────────┐
                              │      Service Layer       │
                              └────────────┬────────────┘
                                           │
                          ┌────────────────┼────────────────┐
                          ▼                ▼                ▼
                     Redis Cache        MySQL DB      BlockingQueue
                    (cache-aside)    (source of         (async
                     24h TTL          truth)           analytics)
                                                           │
                                                    Batch Worker
                                                  (every 5 seconds)
                                                           │
                                                    Analytics DB
```

## DSA Concepts Used

| Concept | Where | Complexity |
|---|---|---|
| **Base62 encoding** | Short code generation | O(log₆₂ n) |
| **LRU Cache (Redis)** | URL resolution caching | O(1) |
| **Token Bucket** | Rate limiter | O(1) amortized |
| **ConcurrentHashMap + AtomicInteger** | Thread-safe rate limiter state | O(1) |
| **LinkedBlockingQueue** | Async analytics pipeline | O(1) enqueue/dequeue |
| **Batch processing** | Analytics flush worker | O(k) per batch |

## Tech Stack

- **Java** · **Spring Boot**
- **MySQL 8** — primary data store, JPA/Hibernate ORM
- **Redis 7** — URL cache (cache-aside, 24h TTL)
- **Spring Security + JWT** — stateless authentication
- **Springdoc OpenAPI** — auto-generated Swagger UI
- **JUnit 5 + Mockito** — unit tests
- **Docker + Docker Compose** — one-command deployment

---

## Quick Start

### Option 1: Docker (recommended, zero setup)

```bash
git clone <your-repo-url>
cd smarturl
docker-compose up --build
```

Visit: http://localhost:8080/swagger-ui.html or http://localhost:8080/swagger-ui/index.html

### Option 2: Run locally (requires MySQL + Redis installed)

```bash
# Start MySQL and Redis (or use homebrew / apt)
# MySQL: CREATE DATABASE smarturl; CREATE USER 'smarturl_user'@'localhost' IDENTIFIED BY 'smarturl_pass'; GRANT ALL ON smarturl.* TO 'smarturl_user'@'localhost';

mvn spring-boot:run
```

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get JWT token |

### URL Operations (JWT required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/urls` | Shorten a URL |
| GET | `/api/urls` | List my URLs (paginated) |
| DELETE | `/api/urls/{code}` | Delete a URL |
| GET | `/api/urls/{code}/analytics` | Click analytics |

### Public

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{shortCode}` | Redirect (HTTP 302) |
| GET | `/api/health` | Health + queue depth |

---

## Sample API Calls

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"secret123"}'

# 2. Login → copy the token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"secret123"}'

# 3. Shorten a URL
curl -X POST http://localhost:8080/api/urls \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://github.com","expiresInDays":30}'

# 4. Follow short link (will redirect)
curl -L http://localhost:8080/<shortCode>

# 5. Analytics
curl http://localhost:8080/api/urls/<shortCode>/analytics \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Running Tests

```bash
mvn test
```

Tests use H2 in-memory DB — no MySQL/Redis needed.

---

## What I'd Add Next

- **Consistent hashing** for multi-node Redis sharding (TreeMap-based hash ring — prototyped)
- **Bloom filter** to check URL existence before DB hit
- **Kafka** instead of in-memory queue for durable analytics
- **Custom domain** support per user
- **QR code** generation endpoint
- Horizontal scaling behind a load balancer

---

## Project Structure

```
src/main/java/com/smarturl/
├── SmartUrlApplication.java        # Entry point
├── config/
│   ├── SecurityConfig.java         # Spring Security + JWT filter chain
│   ├── RedisConfig.java            # Cache manager, TTL config
│   ├── WebConfig.java              # Rate limiter interceptor registration
│   └── OpenApiConfig.java          # Swagger/OpenAPI setup
├── controller/
│   ├── UrlController.java          # Redirect + CRUD endpoints
│   └── AuthController.java         # Register / login
├── service/
│   ├── UrlService.java             # Shorten, resolve, delete logic
│   ├── AnalyticsService.java       # Async pipeline (BlockingQueue)
│   └── AuthService.java            # User auth logic
├── util/
│   ├── Base62Encoder.java          # ★ Core DSA: number base conversion
│   ├── TokenBucketRateLimiter.java # ★ Core DSA: token bucket algorithm
│   └── RateLimitInterceptor.java   # Spring MVC interceptor
├── security/
│   ├── JwtService.java             # JWT generate / validate
│   └── JwtAuthFilter.java          # Once-per-request JWT filter
├── entity/                         # JPA entities
├── repository/                     # Spring Data JPA repos
├── dto/                            # Request/Response objects
└── exception/                      # Custom exceptions + global handler
```
