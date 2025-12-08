# OpenWallet Microservices API - Implementation Plan

## 1. High-Level Architecture Summary

### Architecture Overview

The OpenWallet platform follows a **microservices architecture** with **event-driven communication** via Apache Kafka. Services are loosely coupled, communicate asynchronously for non-critical paths, and maintain their own data stores where appropriate.

**Core Services:**

- **Auth Service**: Handles authentication and authorization (delegates to Keycloak)
- **Customer & KYC Service**: Manages customer profiles and KYC lifecycle
- **Wallet Service**: Wallet lifecycle and state management
- **Ledger/Transactions Service**: Core transaction processing with double-entry accounting
- **Notification Service**: Event-driven notifications (SMS/email simulation)

**Infrastructure Components:**

- **PostgreSQL**: Primary database for all services (can be shared or per-service; we'll use shared for simplicity in 8 hours)
- **Redis**: Caching layer (balances, sessions, rate limiting)
- **Apache Kafka**: Event streaming platform for inter-service communication
- **Keycloak**: Identity and access management
- **Prometheus + Grafana**: Metrics and monitoring
- **Spring Boot Actuator**: Health checks and observability

### Communication Patterns

- **Synchronous**: REST APIs for user-facing operations (wallet creation, balance checks, transaction initiation)
- **Asynchronous**: Kafka events for transaction state changes, notifications, audit logging
- **Service-to-Service**: Direct REST calls for critical paths (e.g., Wallet → Ledger for balance validation), Kafka for eventual consistency

### Key Architectural Patterns

- **Layered Architecture**: Controller → Service → Repository (standard Spring Boot)
- **Domain-Driven Design**: Core entities (Wallet, Transaction, LedgerEntry) as aggregates with business logic
- **Event Sourcing (lightweight)**: Transaction events published to Kafka for audit trail
- **CQRS (basic)**: Separate read models for balance queries (cached in Redis) vs. write models (PostgreSQL)

### Data Flow Examples

**Deposit Flow:**

1. Client → Wallet Service: `POST /api/v1/wallets/{walletId}/deposits`
2. Wallet Service validates wallet exists and is active
3. Wallet Service → Ledger Service: `POST /api/v1/transactions` (synchronous)
4. Ledger Service creates double-entry (debit cash account, credit wallet)
5. Ledger Service publishes `TRANSACTION_COMPLETED` to Kafka
6. Wallet Service consumes event, updates Redis balance cache
7. Notification Service consumes event, sends SMS/email

**Transfer Flow:**

1. Client → Ledger Service: `POST /api/v1/transactions/transfers`
2. Ledger Service validates both wallets, checks balances
3. Creates two ledger entries atomically (debit sender, credit receiver)
4. Publishes `TRANSACTION_COMPLETED` (with sender/receiver wallet IDs)
5. Wallet Service consumes, invalidates/updates Redis cache for both wallets
6. Notification Service sends notifications to both parties

---

## 2. Service-by-Service Breakdown

### 2.1 Auth Service

**Responsibility:**
Delegates authentication to Keycloak, issues JWT tokens, manages role-based access control (RBAC). Provides endpoints for user registration, login, token refresh, and logout. Validates tokens in middleware for other services.

**Main Modules:**

- `com.openwallet.auth.controller` - REST endpoints
- `com.openwallet.auth.service` - Business logic, Keycloak integration
- `com.openwallet.auth.config` - Security configuration, JWT validation
- `com.openwallet.auth.dto` - Request/response DTOs

**Key Entities:**

- `User` (if storing user metadata locally; otherwise just JWT claims)
- `Role` (enum: USER, ADMIN, AUDITOR)

**Database:**

- Minimal local storage (optional user preferences table)
- Primary identity stored in Keycloak

**Kafka Topics:**

- Produces: `USER_REGISTERED`, `USER_LOGIN`, `USER_LOGOUT` (for audit)

---

### 2.2 Customer & KYC Service

**Responsibility:**
Manages customer profiles (name, phone, email, address), KYC status lifecycle (PENDING, IN_PROGRESS, VERIFIED, REJECTED), and webhook endpoints to simulate external KYC provider callbacks.

**Main Modules:**

- `com.openwallet.customer.controller` - Customer CRUD, KYC endpoints
- `com.openwallet.customer.service` - Customer and KYC business logic
- `com.openwallet.customer.domain` - Customer, KycCheck entities
- `com.openwallet.customer.repository` - JPA repositories
- `com.openwallet.customer.webhook` - External KYC provider webhook handler

**Key Entities:**

- `Customer` (id, userId, firstName, lastName, phoneNumber, email, address, status, createdAt, updatedAt)
- `KycCheck` (id, customerId, status, initiatedAt, verifiedAt, rejectionReason, providerReference, documents)

**Database Tables:**

- `customers` (owned by this service)
- `kyc_checks` (owned by this service)

**Kafka Topics:**

- Produces: `KYC_INITIATED`, `KYC_VERIFIED`, `KYC_REJECTED`
- Consumes: None (but could consume USER_REGISTERED to auto-create customer)

---

### 2.3 Wallet Service

**Responsibility:**
Manages wallet lifecycle (creation, activation, suspension, closure), wallet state validation, balance queries (with Redis caching), and wallet limits (daily/monthly transaction limits). Acts as the primary interface for wallet operations.

**Main Modules:**

- `com.openwallet.wallet.controller` - Wallet CRUD, balance queries
- `com.openwallet.wallet.service` - Wallet business logic, Redis integration
- `com.openwallet.wallet.domain` - Wallet entity
- `com.openwallet.wallet.repository` - JPA repository
- `com.openwallet.wallet.cache` - Redis cache manager for balances

**Key Entities:**

- `Wallet` (id, customerId, currency, status, balance, createdAt, updatedAt, dailyLimit, monthlyLimit)

**Database Tables:**

- `wallets` (owned by this service)

**Kafka Topics:**

- Produces: `WALLET_CREATED`, `WALLET_SUSPENDED`, `WALLET_ACTIVATED`
- Consumes: `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED` (to update balance cache)

**Redis Keys:**

- `wallet:balance:{walletId}` - TTL: 5 minutes
- `wallet:lock:{walletId}` - Distributed lock for concurrent operations, TTL: 30 seconds

---

### 2.4 Ledger/Transactions Service

**Responsibility:**
Core transaction processing with double-entry bookkeeping. Handles deposits, withdrawals, and P2P transfers. Ensures ACID semantics, maintains audit trail, and publishes transaction events to Kafka.

**Main Modules:**

- `com.openwallet.ledger.controller` - Transaction endpoints
- `com.openwallet.ledger.service` - Transaction processing, double-entry logic
- `com.openwallet.ledger.domain` - Transaction, LedgerEntry entities
- `com.openwallet.ledger.repository` - JPA repositories
- `com.openwallet.ledger.events` - Kafka producer for transaction events

**Key Entities:**

- `Transaction` (id, type, amount, currency, fromWalletId, toWalletId, status, initiatedAt, completedAt, failureReason, idempotencyKey)
- `LedgerEntry` (id, transactionId, walletId, accountType, entryType, amount, balanceAfter, createdAt)

**Database Tables:**

- `transactions` (owned by this service)
- `ledger_entries` (owned by this service)

**Kafka Topics:**

- Produces: `TRANSACTION_INITIATED`, `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED`
- Consumes: None (but could consume WALLET_SUSPENDED to reject transactions)

**Double-Entry Logic:**

- Deposit: Debit `CASH_ACCOUNT`, Credit `WALLET_{walletId}`
- Withdrawal: Debit `WALLET_{walletId}`, Credit `CASH_ACCOUNT`
- Transfer: Debit `WALLET_{senderId}`, Credit `WALLET_{receiverId}`

---

### 2.5 Notification Service

**Responsibility:**
Listens to Kafka events and sends notifications (SMS/email). Simulates external notification providers. Maintains notification history for audit.

**Main Modules:**

- `com.openwallet.notification.listener` - Kafka event consumers
- `com.openwallet.notification.service` - Notification sending logic
- `com.openwallet.notification.provider` - SMS/Email adapter interfaces
- `com.openwallet.notification.domain` - Notification entity (optional, for history)

**Key Entities:**

- `Notification` (id, recipient, type, channel, content, status, sentAt) - optional

**Database Tables:**

- `notifications` (optional, for history)

**Kafka Topics:**

- Consumes: `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED`, `KYC_VERIFIED`, `KYC_REJECTED`
- Produces: None

**Notification Channels:**

- SMS (simulated via logging)
- Email (simulated via logging, or simple SMTP adapter)

---

### 2.6 Reporting/Statements Service (Optional)

**Responsibility:**
Generates transaction statements, monthly summaries, and aggregated reports. Can be implemented if time allows.

**Main Modules:**

- `com.openwallet.reporting.controller` - Statement endpoints
- `com.openwallet.reporting.service` - Aggregation logic
- `com.openwallet.reporting.domain` - Statement entity

**Database:**

- Read-only access to `transactions` and `ledger_entries` (via shared DB or API calls)

**Kafka Topics:**

- Consumes: `TRANSACTION_COMPLETED` (for real-time aggregation, optional)

---

## 3. Data Model & Schema Design

### 3.1 PostgreSQL Tables

#### `customers`

```sql
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE, -- Links to Keycloak user ID
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    address TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CLOSED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_user_id ON customers(user_id);
CREATE INDEX idx_customers_phone ON customers(phone_number);
CREATE INDEX idx_customers_status ON customers(status);
```

#### `kyc_checks`

```sql
CREATE TABLE kyc_checks (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, VERIFIED, REJECTED
    provider_reference VARCHAR(255),
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    rejection_reason TEXT,
    documents JSONB, -- Store document metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kyc_customer_id ON kyc_checks(customer_id);
CREATE INDEX idx_kyc_status ON kyc_checks(status);
CREATE UNIQUE INDEX idx_kyc_customer_latest ON kyc_checks(customer_id, created_at DESC);
```

#### `wallets`

```sql
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES', -- ISO currency code
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CLOSED
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    daily_limit DECIMAL(19, 2) NOT NULL DEFAULT 100000.00,
    monthly_limit DECIMAL(19, 2) NOT NULL DEFAULT 1000000.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_customer_currency UNIQUE (customer_id, currency)
);

CREATE INDEX idx_wallets_customer_id ON wallets(customer_id);
CREATE INDEX idx_wallets_status ON wallets(status);
CREATE INDEX idx_wallets_currency ON wallets(currency);
```

#### `transactions`

```sql
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAWAL, TRANSFER
    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    from_wallet_id BIGINT REFERENCES wallets(id),
    to_wallet_id BIGINT REFERENCES wallets(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, CANCELLED
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    failure_reason TEXT,
    idempotency_key VARCHAR(255) UNIQUE, -- For idempotent operations
    metadata JSONB, -- Additional transaction metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_transfer_wallets CHECK (
        (transaction_type = 'TRANSFER' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL) OR
        (transaction_type = 'DEPOSIT' AND to_wallet_id IS NOT NULL AND from_wallet_id IS NULL) OR
        (transaction_type = 'WITHDRAWAL' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NULL)
    )
);

CREATE INDEX idx_transactions_from_wallet ON transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet ON transactions(to_wallet_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_initiated_at ON transactions(initiated_at DESC);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);
CREATE INDEX idx_transactions_customer_lookup ON transactions(from_wallet_id, to_wallet_id, initiated_at DESC);
```

#### `ledger_entries`

```sql
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    wallet_id BIGINT REFERENCES wallets(id),
    account_type VARCHAR(50) NOT NULL, -- WALLET_{walletId}, CASH_ACCOUNT, FEE_ACCOUNT
    entry_type VARCHAR(10) NOT NULL, -- DEBIT, CREDIT
    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(19, 2) NOT NULL, -- Running balance after this entry
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_account_type ON ledger_entries(account_type);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at DESC);
```

#### `notifications` (Optional)

```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL, -- Phone or email
    notification_type VARCHAR(50) NOT NULL, -- TRANSACTION_COMPLETED, KYC_VERIFIED, etc.
    channel VARCHAR(20) NOT NULL, -- SMS, EMAIL
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
```

### 3.2 Redis Usage

#### Balance Cache

- **Key Pattern**: `wallet:balance:{walletId}`
- **Value**: JSON `{"balance": "1000.00", "currency": "KES", "updatedAt": "2024-01-01T12:00:00Z"}`
- **TTL**: 5 minutes
- **Strategy**: Write-through on transaction completion, invalidate on wallet suspension

#### Distributed Locks

- **Key Pattern**: `wallet:lock:{walletId}`
- **Value**: Lock holder identifier (UUID)
- **TTL**: 30 seconds
- **Use Case**: Prevent concurrent balance updates during transfers

#### Rate Limiting

- **Key Pattern**: `rate:limit:{userId}:{operation}:{timeWindow}`
- **Value**: Counter (integer)
- **TTL**: Time window (e.g., 1 hour for hourly limits)
- **Strategy**: Increment on each operation, check against limit

#### Session Cache (if not using Keycloak sessions)

- **Key Pattern**: `session:{sessionId}`
- **Value**: User ID, roles (JSON)
- **TTL**: 30 minutes (refresh on access)

---

## 4. API Design

### 4.1 Auth Service

| Method | Endpoint                | Description          | Request/Response                                                                      |
| ------ | ----------------------- | -------------------- | ------------------------------------------------------------------------------------- |
| POST   | `/api/v1/auth/register` | Register new user    | Request: `{username, email, password}`<br>Response: `{userId, message}`               |
| POST   | `/api/v1/auth/login`    | Authenticate user    | Request: `{username, password}`<br>Response: `{accessToken, refreshToken, expiresIn}` |
| POST   | `/api/v1/auth/refresh`  | Refresh access token | Request: `{refreshToken}`<br>Response: `{accessToken, expiresIn}`                     |
| POST   | `/api/v1/auth/logout`   | Logout user          | Request: `{refreshToken}`<br>Response: `{message}`                                    |

### 4.2 Customer & KYC Service

| Method | Endpoint                            | Description                         | Request/Response                                                                             |
| ------ | ----------------------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/customers/me`              | Get current user's customer profile | Response: `{id, firstName, lastName, phoneNumber, email, address, status}`                   |
| PUT    | `/api/v1/customers/me`              | Update customer profile             | Request: `{firstName, lastName, phoneNumber, email, address}`<br>Response: Updated customer  |
| POST   | `/api/v1/customers/me/kyc/initiate` | Initiate KYC check                  | Request: `{documents: [...]}`<br>Response: `{kycCheckId, status, message}`                   |
| GET    | `/api/v1/customers/me/kyc/status`   | Get KYC status                      | Response: `{status, verifiedAt, rejectionReason}`                                            |
| POST   | `/api/v1/customers/kyc/webhook`     | Webhook for external KYC provider   | Request: `{providerReference, status, verifiedAt, rejectionReason}`<br>Response: `{message}` |

### 4.3 Wallet Service

| Method | Endpoint                              | Description                  | Request/Response                                                                  |
| ------ | ------------------------------------- | ---------------------------- | --------------------------------------------------------------------------------- |
| POST   | `/api/v1/wallets`                     | Create new wallet            | Request: `{currency}`<br>Response: `{id, customerId, currency, status, balance}`  |
| GET    | `/api/v1/wallets/{walletId}`          | Get wallet details           | Response: `{id, customerId, currency, status, balance, dailyLimit, monthlyLimit}` |
| GET    | `/api/v1/wallets/me`                  | Get current user's wallets   | Response: `[{wallet1}, {wallet2}, ...]`                                           |
| GET    | `/api/v1/wallets/{walletId}/balance`  | Get wallet balance (cached)  | Response: `{balance, currency, lastUpdated}`                                      |
| PUT    | `/api/v1/wallets/{walletId}/suspend`  | Suspend wallet (admin only)  | Response: `{message}`                                                             |
| PUT    | `/api/v1/wallets/{walletId}/activate` | Activate wallet (admin only) | Response: `{message}`                                                             |

### 4.4 Ledger/Transactions Service

| Method | Endpoint                                          | Description                        | Request/Response                                                                                                       |
| ------ | ------------------------------------------------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| POST   | `/api/v1/transactions/deposits`                   | Create deposit transaction         | Request: `{toWalletId, amount, currency, idempotencyKey}`<br>Response: `{transactionId, status, amount}`               |
| POST   | `/api/v1/transactions/withdrawals`                | Create withdrawal transaction      | Request: `{fromWalletId, amount, currency, idempotencyKey}`<br>Response: `{transactionId, status, amount}`             |
| POST   | `/api/v1/transactions/transfers`                  | Create P2P transfer                | Request: `{fromWalletId, toWalletId, amount, currency, idempotencyKey}`<br>Response: `{transactionId, status, amount}` |
| GET    | `/api/v1/transactions/{transactionId}`            | Get transaction details            | Response: `{id, type, amount, status, fromWalletId, toWalletId, initiatedAt, completedAt}`                             |
| GET    | `/api/v1/transactions`                            | Get transaction history            | Query: `?walletId={id}&page=0&size=20&fromDate=&toDate=`<br>Response: `{content: [...], totalElements, totalPages}`    |
| GET    | `/api/v1/transactions/wallets/{walletId}/history` | Get wallet transaction history     | Query: `?page=0&size=20`<br>Response: Paginated transaction list                                                       |
| GET    | `/api/v1/ledger/entries`                          | Get ledger entries (admin/auditor) | Query: `?accountType=&fromDate=&toDate=&page=0&size=20`<br>Response: Paginated ledger entries                          |

### 4.5 Notification Service

| Method | Endpoint                   | Description                     | Request/Response                                              |
| ------ | -------------------------- | ------------------------------- | ------------------------------------------------------------- |
| GET    | `/api/v1/notifications/me` | Get user's notification history | Query: `?page=0&size=20`<br>Response: Paginated notifications |

### 4.6 Reporting Service (Optional)

| Method | Endpoint                                     | Description           | Request/Response                                                                                      |
| ------ | -------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/statements/{walletId}/monthly`      | Get monthly statement | Query: `?year=2024&month=1`<br>Response: `{walletId, period, transactions: [...], summary}`           |
| GET    | `/api/v1/reports/wallets/{walletId}/summary` | Get wallet summary    | Query: `?fromDate=&toDate=`<br>Response: `{totalDeposits, totalWithdrawals, totalTransfers, balance}` |

---

## 5. Cross-Cutting Concerns

### 5.1 Security

**JWT/OAuth2 Handling:**

- All services validate JWT tokens via a shared library or Spring Security configuration
- Token validation: Extract `Authorization: Bearer {token}` header, validate signature with Keycloak public key
- Claims extraction: User ID, roles, permissions stored in JWT claims
- Token refresh: Auth service handles refresh token rotation

**Role-Based Access Control (RBAC):**

- **USER**: Can access own wallets, transactions, initiate KYC
- **ADMIN**: Can suspend/activate wallets, view all transactions, access ledger entries
- **AUDITOR**: Read-only access to all transactions and ledger entries

**Implementation:**

- Spring Security `@PreAuthorize` annotations on controller methods
- Custom `SecurityConfig` in each service
- Shared JWT validation filter/interceptor

### 5.2 Validation

**Request Validation:**

- Bean Validation (`@Valid`, `@NotNull`, `@Min`, `@Max`, `@Email`, `@Pattern`)
- Custom validators for business rules (e.g., wallet status, KYC requirements)
- Validation error responses: `400 Bad Request` with detailed field errors

**Example:**

```java
@PostMapping("/transfers")
public ResponseEntity<TransactionResponse> transfer(
    @Valid @RequestBody TransferRequest request) {
    // ...
}
```

### 5.3 Error Handling

**Common Error Response Format:**

```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/transactions/transfers",
  "details": [
    {
      "field": "amount",
      "message": "Amount must be greater than 0"
    }
  ]
}
```

**Exception Mapping:**

- `@ControllerAdvice` in each service
- Map exceptions: `IllegalArgumentException` → 400, `EntityNotFoundException` → 404, `InsufficientBalanceException` → 422, `UnauthorizedException` → 401, `ForbiddenException` → 403
- Log exceptions with correlation IDs for tracing

### 5.4 Logging & Observability

**Structured Logging:**

- Use SLF4J with Logback, JSON format for production
- Include: `timestamp`, `level`, `service`, `correlationId`, `userId`, `message`, `stackTrace`
- Correlation ID: Generated at request entry, propagated via `MDC` or HTTP headers

**Prometheus Metrics:**

- Custom metrics: `wallet_transactions_total`, `wallet_balance_current`, `transaction_duration_seconds`, `kafka_messages_consumed_total`
- Spring Boot Actuator endpoints: `/actuator/metrics`, `/actuator/prometheus`
- Health checks: `/actuator/health` (includes DB, Redis, Kafka connectivity)

**Distributed Tracing:**

- Optional: Add Spring Cloud Sleuth/Zipkin if time allows (can be de-scoped)

### 5.5 Resilience

**Circuit Breaker:**

- Use Resilience4j for inter-service calls (e.g., Wallet → Ledger)
- Configuration: Failure threshold 50%, wait duration 60s, half-open state after 30s

**Retry:**

- Retry transient failures (network, DB connection) with exponential backoff
- Idempotent operations: Retry safe (use idempotency keys)

**Timeout:**

- HTTP client timeouts: 5 seconds for synchronous calls
- Kafka consumer timeouts: 30 seconds

**Bulkhead:**

- Separate thread pools for critical vs. non-critical operations

---

## 6. DevSecOps & CI/CD Plan

### 6.1 GitHub Actions Workflow

**Workflow File**: `.github/workflows/ci-cd.yml`

**Steps:**

1. **Checkout**: Check out code
2. **Set up JDK 17**: Use `actions/setup-java@v3` with distribution `temurin`
3. **Cache Dependencies**: Cache Maven/Gradle dependencies
4. **Build**: Run `./mvnw clean compile` (or `./gradlew build`)
5. **Run Tests**: Execute unit and integration tests
6. **Generate Coverage Report**: Use JaCoCo, publish to coverage service (optional)
7. **Static Analysis**:
   - SpotBugs: `mvn spotbugs:check`
   - Checkstyle: `mvn checkstyle:check` (optional)
   - Dependency Check: `mvn org.owasp:dependency-check-maven:check`
8. **Build Docker Images**: Build images for each service, tag with commit SHA
9. **Push to Registry**: Push to Docker Hub or GitHub Container Registry (if configured)
10. **Deploy to Demo** (optional): Deploy to demo environment if on `main` branch

**Workflow Triggers:**

- On push to `main`, `develop`
- On pull request
- Manual trigger

### 6.2 Static Analysis & Security

**Tools:**

- **SpotBugs**: Find bugs and potential issues
- **OWASP Dependency Check**: Scan for vulnerable dependencies
- **SonarQube** (optional, if cloud instance available): Code quality and security

**Security Practices:**

- No secrets in code (use environment variables or secrets management)
- Dependency vulnerability scanning in CI
- Docker image scanning (Trivy, if time allows)

### 6.3 Docker Compose Setup

**Services:**

```yaml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: openwallet
      POSTGRES_USER: openwallet
      POSTGRES_PASSWORD: openwallet
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  keycloak:
    image: quay.io/keycloak/keycloak:latest
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8080:8080"
    command: start-dev

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    depends_on:
      - prometheus

  # Application services (built locally)
  auth-service:
    build: ./auth-service
    ports:
      - "8081:8080"
    depends_on:
      - postgres
      - redis
      - keycloak

  customer-service:
    build: ./customer-service
    ports:
      - "8082:8080"
    depends_on:
      - postgres
      - kafka

  wallet-service:
    build: ./wallet-service
    ports:
      - "8083:8080"
    depends_on:
      - postgres
      - redis
      - kafka

  ledger-service:
    build: ./ledger-service
    ports:
      - "8084:8080"
    depends_on:
      - postgres
      - kafka

  notification-service:
    build: ./notification-service
    ports:
      - "8085:8080"
    depends_on:
      - postgres
      - kafka
```

### 6.4 Environment Configuration

**Profiles:**

- `local`: Development with Docker Compose
- `demo`: Demo environment (similar to local but with different DB credentials)

**Configuration Files:**

- `application.yml`: Base configuration
- `application-local.yml`: Local overrides (Docker Compose service names)
- `application-demo.yml`: Demo overrides

**Secrets Management:**

- Use environment variables for sensitive data (DB passwords, Keycloak secrets)
- `.env` file for local development (not committed)
- GitHub Secrets for CI/CD

---

## 7. Repository & Folder Structure

### 7.1 Monorepo Structure (Preferred)

```
open-wallet/
├── .github/
│   └── workflows/
│       └── ci-cd.yml
├── docker-compose.yml
├── docker-compose.override.yml (optional)
├── pom.xml (parent POM for multi-module)
├── README.md
├── docs/
│   ├── API.md
│   └── ARCHITECTURE.md
├── auth-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/openwallet/auth/
│       │   │   ├── AuthServiceApplication.java
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── config/
│       │   │   ├── dto/
│       │   │   └── security/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── application-local.yml
│       └── test/
├── customer-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/openwallet/customer/
│       │   │   ├── CustomerServiceApplication.java
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── domain/
│       │   │   ├── repository/
│       │   │   ├── dto/
│       │   │   └── webhook/
│       │   └── resources/
│       │       ├── db/migration/ (Flyway)
│       │       └── application.yml
│       └── test/
├── wallet-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/openwallet/wallet/
│       │   │   ├── WalletServiceApplication.java
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── domain/
│       │   │   ├── repository/
│       │   │   ├── cache/
│       │   │   └── dto/
│       │   └── resources/
│       └── test/
├── ledger-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/openwallet/ledger/
│       │   │   ├── LedgerServiceApplication.java
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── domain/
│       │   │   ├── repository/
│       │   │   ├── events/
│       │   │   └── dto/
│       │   └── resources/
│       └── test/
├── notification-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/openwallet/notification/
│       │   │   ├── NotificationServiceApplication.java
│       │   │   ├── listener/
│       │   │   ├── service/
│       │   │   ├── provider/
│       │   │   └── domain/
│       │   └── resources/
│       └── test/
├── shared-lib/ (optional)
│   ├── pom.xml
│   └── src/main/java/com/openwallet/shared/
│       ├── dto/
│       ├── events/
│       └── security/
└── scripts/
    ├── seed-data.sql
    └── start-all.sh
```

### 7.2 Sample Service Structure (Wallet Service Example)

```
wallet-service/src/main/java/com/openwallet/wallet/
├── WalletServiceApplication.java
├── controller/
│   └── WalletController.java
├── service/
│   ├── WalletService.java
│   └── BalanceCacheService.java
├── domain/
│   └── Wallet.java (JPA entity)
├── repository/
│   └── WalletRepository.java (JPA repository)
├── cache/
│   └── RedisCacheManager.java
├── dto/
│   ├── WalletRequest.java
│   ├── WalletResponse.java
│   └── BalanceResponse.java
├── config/
│   ├── RedisConfig.java
│   ├── SecurityConfig.java
│   └── KafkaConfig.java
└── exception/
    ├── WalletNotFoundException.java
    └── WalletExceptionHandler.java
```

---

## 8. 8-Hour Execution Plan (Time-Boxed Roadmap)

### Phase 1: Project & Infrastructure Setup (Hour 0.0 – 1.5)

**Tasks:**

1. Initialize Maven multi-module project structure

   - Create parent `pom.xml` with Spring Boot BOM, dependency management
   - Create module directories and `pom.xml` for each service
   - Add Spring Boot starters (Web, Data JPA, Kafka, Redis, Actuator, Security)

2. Create Docker Compose file

   - Add services: PostgreSQL, Redis, Kafka, Zookeeper, Keycloak, Prometheus, Grafana
   - Configure environment variables and volumes
   - Test infrastructure startup

3. Add base Spring Boot application classes

   - Create `*ServiceApplication.java` for each service with `@SpringBootApplication`
   - Add basic `application.yml` with server port, DB connection (placeholder)

4. Configure logging

   - Add Logback configuration with JSON format
   - Add correlation ID MDC filter

5. Add Spring Boot Actuator
   - Enable health, metrics, prometheus endpoints
   - Configure health checks for DB, Redis, Kafka

**Deliverables:**

- ✅ Monorepo structure with all modules
- ✅ Docker Compose running all infrastructure
- ✅ All services start (even if empty)
- ✅ Basic logging and health checks working

---

### Phase 2: Core Domain & Schema Setup (Hour 1.5 – 3.0)

**Tasks:**

1. Design and create database schema

   - Create Flyway migration scripts for all tables
   - Add indexes and constraints
   - Test migrations on clean DB

2. Create JPA entities

   - `Customer`, `KycCheck`, `Wallet`, `Transaction`, `LedgerEntry`
   - Add relationships, validations, audit fields (`@CreatedDate`, `@LastModifiedDate`)

3. Create JPA repositories

   - `CustomerRepository`, `WalletRepository`, `TransactionRepository`, `LedgerEntryRepository`
   - Add custom query methods where needed

4. Add database configuration

   - Configure JPA/Hibernate in `application.yml`
   - Add Flyway configuration
   - Test entity persistence

5. Create basic DTOs
   - Request/Response DTOs for each service (minimal, expand later)

**Deliverables:**

- ✅ Database schema created and migrated
- ✅ All entities defined with relationships
- ✅ Repositories functional
- ✅ Can persist test data manually

---

### Phase 3: Wallet & Ledger Flows (Hour 3.0 – 4.5)

**Tasks:**

1. Implement Wallet Service core APIs

   - `POST /wallets` - Create wallet
   - `GET /wallets/{id}` - Get wallet
   - `GET /wallets/me` - Get user's wallets
   - Add service layer with business logic
   - Add validation and error handling

2. Implement Ledger Service core APIs

   - `POST /transactions/deposits` - Deposit
   - `POST /transactions/withdrawals` - Withdrawal
   - `POST /transactions/transfers` - Transfer
   - Implement double-entry logic in service layer
   - Add transaction status management (PENDING → COMPLETED/FAILED)
   - Add idempotency key handling

3. Wire Kafka for transaction events

   - Create Kafka producer in Ledger Service
   - Publish `TRANSACTION_INITIATED`, `TRANSACTION_COMPLETED`, `TRANSACTION_FAILED`
   - Create Kafka consumer in Wallet Service
   - Update Redis balance cache on `TRANSACTION_COMPLETED`

4. Add Redis balance caching

   - Create `BalanceCacheService` in Wallet Service
   - Cache balance on wallet read, invalidate on transaction completion
   - Add distributed lock for concurrent operations

5. Add basic integration tests
   - Test deposit flow end-to-end
   - Test transfer flow
   - Verify double-entry balances

**Deliverables:**

- ✅ Wallet creation and retrieval working
- ✅ Deposit, withdrawal, transfer APIs functional
- ✅ Double-entry ledger working correctly
- ✅ Kafka events published and consumed
- ✅ Redis caching working

---

### Phase 4: KYC & Notifications (Hour 4.5 – 6.0)

**Tasks:**

1. Implement Customer Service APIs

   - `GET /customers/me` - Get profile
   - `PUT /customers/me` - Update profile
   - Add service layer

2. Implement KYC flow

   - `POST /customers/me/kyc/initiate` - Initiate KYC
   - `GET /customers/me/kyc/status` - Get status
   - `POST /customers/kyc/webhook` - Webhook handler (simulate external provider)
   - Add KYC status state machine (PENDING → IN_PROGRESS → VERIFIED/REJECTED)
   - Publish Kafka events: `KYC_INITIATED`, `KYC_VERIFIED`, `KYC_REJECTED`

3. Implement Notification Service

   - Create Kafka consumers for transaction and KYC events
   - Implement SMS provider (logging simulation)
   - Implement Email provider (logging simulation)
   - Add notification history (optional table)

4. Add basic tests
   - Test KYC initiation and webhook callback
   - Test notification sending on transaction events

**Deliverables:**

- ✅ Customer profile management working
- ✅ KYC flow functional with webhook simulation
- ✅ Notification Service consuming events and sending notifications
- ✅ Basic tests passing

---

### Phase 5: Hardening & DevSecOps (Hour 6.0 – 7.0)

**Tasks:**

1. Add security and authentication

   - Configure Keycloak integration (or Spring Security with JWT)
   - Add JWT validation filter/interceptor to all services
   - Add `@PreAuthorize` annotations for RBAC
   - Test with different user roles

2. Add comprehensive validation

   - Add Bean Validation annotations to all DTOs
   - Add custom validators for business rules
   - Improve error responses with detailed field errors

3. Add resilience patterns

   - Add Resilience4j circuit breaker for inter-service calls
   - Add retry logic for transient failures
   - Add timeouts

4. Add observability

   - Add custom Prometheus metrics (transaction counts, durations)
   - Improve structured logging with correlation IDs
   - Add distributed tracing (optional, if time allows)

5. Create GitHub Actions workflow

   - Add workflow file with build, test, static analysis steps
   - Configure SpotBugs and OWASP Dependency Check
   - Test workflow on push

6. Add Dockerfiles
   - Create `Dockerfile` for each service
   - Test building images
   - Update Docker Compose to use built images

**Deliverables:**

- ✅ Authentication and authorization working
- ✅ Comprehensive validation and error handling
- ✅ Resilience patterns in place
- ✅ CI/CD pipeline functional
- ✅ All services containerized

---

### Phase 6: Documentation & Polish (Hour 7.0 – 8.0)

**Tasks:**

1. Write comprehensive README

   - Architecture overview
   - Setup instructions
   - How to run locally
   - API documentation links

2. Create API documentation

   - Add OpenAPI/Swagger annotations to controllers
   - Generate Swagger UI (or use SpringDoc)
   - Document request/response examples

3. Add sample data seeding

   - Create SQL script or Java data seeder
   - Seed test users, customers, wallets
   - Add instructions for demo data

4. Create example requests

   - Add Postman collection or curl examples
   - Document authentication flow
   - Add example transaction flows

5. Final refactoring pass

   - Code cleanup
   - Remove unused imports
   - Improve naming consistency
   - Add missing JavaDoc for public APIs

6. Test end-to-end flow
   - Register user → Create customer → Initiate KYC → Verify KYC → Create wallet → Deposit → Transfer → Check history
   - Verify all services communicate correctly
   - Check logs and metrics

**Deliverables:**

- ✅ Complete README with setup instructions
- ✅ API documentation accessible
- ✅ Sample data and examples provided
- ✅ All code polished and tested
- ✅ End-to-end flow working

---

## 9. Risk/Scope Trade-offs

### Can Be De-Scoped (Without Hurting Narrative)

1. **Reporting/Statements Service**

   - **Impact**: Low - Core transaction functionality is more important
   - **Alternative**: Mention in architecture as "future enhancement"

2. **Full Keycloak Integration**

   - **Impact**: Low - Can use Spring Security with simple JWT for demo
   - **Alternative**: Mock authentication or simple JWT issuer

3. **Comprehensive Integration Tests**

   - **Impact**: Medium - Unit tests and basic integration tests are sufficient
   - **Alternative**: Focus on critical paths (transactions, wallet operations)

4. **Advanced Observability (Distributed Tracing)**

   - **Impact**: Low - Prometheus metrics and structured logs are sufficient
   - **Alternative**: Add correlation IDs for basic tracing

5. **Full KYC Provider Integration**

   - **Impact**: Low - Webhook simulation is sufficient to show integration pattern
   - **Alternative**: Document how real integration would work

6. **Advanced Resilience (Bulkheads, Advanced Circuit Breaker Config)**
   - **Impact**: Low - Basic retry and circuit breaker are sufficient
   - **Alternative**: Document patterns for production

### Nice-to-Have Features (If Extra Time)

1. **Daily Reconciliation Job**

   - Scheduled job that verifies ledger balances match wallet balances
   - Publishes reconciliation report to Kafka
   - Shows batch processing capabilities

2. **Simple Fraud Rule Engine**

   - Rule-based fraud detection (e.g., velocity checks, large transaction alerts)
   - Publishes `FRAUD_ALERT` events
   - Shows event-driven architecture for business rules

3. **Statement Export as PDF**

   - Generate PDF statements for monthly summaries
   - Shows file generation and export capabilities

4. **Rate Limiting per User/Wallet**

   - Implement Redis-based rate limiting
   - Show practical use of Redis for non-cache scenarios

5. **Transaction Reversal/Cancellation**
   - Add ability to reverse completed transactions
   - Shows complex state management

---

## 10. Success Criteria

By the end of 8 hours, the project should demonstrate:

✅ **Microservices Architecture**: Clear service boundaries, independent deployability  
✅ **Event-Driven Communication**: Kafka events flowing between services  
✅ **Core Fintech Functionality**: Wallet creation, deposits, withdrawals, transfers working  
✅ **Double-Entry Ledger**: Accurate accounting with audit trail  
✅ **Security**: Authentication and authorization in place  
✅ **Observability**: Metrics, logs, health checks functional  
✅ **CI/CD**: GitHub Actions pipeline building and testing  
✅ **Containerization**: All services runnable via Docker Compose  
✅ **Documentation**: Clear README and API docs for reviewers

---

## Next Steps

After reviewing this plan, you can proceed with:

1. **"Generate the Maven multi-module structure based on your plan"**
2. **"Create the Spring Boot skeleton for the Wallet Service as you designed"**
3. **"Set up the Docker Compose file with all infrastructure services"**
4. **"Create the database schema migrations for all tables"**

This plan provides a solid foundation for building a production-ready microservices backend that showcases your skills for a fintech/telco role.
