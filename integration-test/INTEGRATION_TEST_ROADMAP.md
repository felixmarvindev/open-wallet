# Integration Test Roadmap

> **Current Status:** User Onboarding Flow Complete ✅  
> **Next Step:** Automatic Wallet Creation Flow  
> **Last Updated:** December 14, 2025

---

## Table of Contents

1. [Service Ecosystem Overview](#service-ecosystem-overview)
2. [Current Status](#current-status)
3. [Integration Test Roadmap](#integration-test-roadmap)
4. [Next Steps: Wallet Creation Flow](#next-steps-wallet-creation-flow)
5. [Event-Driven Architecture](#event-driven-architecture)
6. [Service Details](#service-details)

---

## Service Ecosystem Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    OPEN WALLET SERVICES                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐       │
│  │     Auth     │   │   Customer   │   │    Wallet    │       │
│  │   Service    │   │   Service    │   │   Service    │       │
│  │  (Port 9001) │   │  (Port 9002) │   │  (Port 9003) │       │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘       │
│         │                  │                   │                │
│         │                  │                   │                │
│  ┌──────▼──────┐   ┌──────▼───────┐   ┌──────▼────────┐       │
│  │   Ledger    │   │ Notification │   │               │       │
│  │   Service   │   │   Service    │   │  Future...    │       │
│  │ (Port 9004) │   │  (Port 9005) │   │               │       │
│  └──────┬──────┘   └──────┬───────┘   └───────────────┘       │
│         │                  │                                    │
│         └─────────┬────────┘                                    │
│                   ▼                                             │
│  ┌────────────────────────────────────────────────────┐        │
│  │              Kafka Event Bus                        │        │
│  │  Topics:                                            │        │
│  │  • user-events (USER_REGISTERED)                   │        │
│  │  • customer-events (CUSTOMER_CREATED)              │        │
│  │  • kyc-events (KYC_VERIFIED, KYC_REJECTED)         │        │
│  │  • wallet-events (WALLET_CREATED)                  │        │
│  │  • transaction-events (TX_COMPLETED, TX_FAILED)    │        │
│  └────────────────────────────────────────────────────┘        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Current Status

### ✅ Services Built & Working

| Service | Features | Status | Notes |
|---------|----------|--------|-------|
| **Auth Service** | Registration, Login, JWT | ✅ Complete | Returns 201 for registration |
| **Customer Service** | Profile creation, KYC | ✅ Complete | KYC workflow implemented |
| **Wallet Service** | Wallet with limits | ✅ Built | Not wired to events yet |
| **Ledger Service** | Double-entry bookkeeping | ✅ Built | Not wired to events yet |
| **Notification Service** | Email/SMS alerts | ✅ Built | Listeners ready for KYC & TX |

### ✅ Integration Tests Passing

| Test | Covers | Status |
|------|--------|--------|
| `ServiceStartupProofTest` | All services start successfully | ✅ Passing |
| `KeycloakConfigurationTest` | Keycloak realm, JWT generation | ✅ Passing |
| `UserOnboardingFlowTest` | Register → Login → Create Profile | ✅ Passing |

### ✅ Infrastructure Working

| Component | Version | Status |
|-----------|---------|--------|
| PostgreSQL | 15-alpine | ✅ Running in TestContainers |
| Kafka | 7.5.0 | ✅ Running in TestContainers |
| Keycloak | 26.0.7 | ✅ Running in TestContainers |
| Realm Configuration | openwallet | ✅ Configured with users & clients |

---

## Integration Test Roadmap

### Phase 1: Foundation (Current → Week 1) 🏗️

| Priority | Test | Description | Time | Complexity | Value |
|----------|------|-------------|------|------------|-------|
| ✅ | User Onboarding | Register → Login → Profile | - | Medium | High |
| **⏳ NEXT** | **Automatic Wallet Creation** | **Customer → Wallet via Kafka** | **3-4h** | **Medium** | **Very High** |
| ⏳ | KYC Verification | KYC → Update Wallet Limits | 2-3h | Medium | High |

### Phase 2: Core Financial Operations (Week 2) 💰

| Priority | Test | Description | Time | Complexity | Value |
|----------|------|-------------|------|------------|-------|
| ⭐⭐⭐ | Deposit Flow | Money IN → Update Balance | 3-4h | Medium-High | Very High |
| ⭐⭐⭐ | P2P Transfer Flow | Wallet A → Wallet B | 4-5h | High | Very High |
| ⭐⭐ | Withdrawal Flow | Money OUT → Update Balance | 3-4h | Medium | High |

### Phase 3: Edge Cases & Error Handling (Week 3) ⚠️

| Priority | Test | Description | Time | Complexity | Value |
|----------|------|-------------|------|------------|-------|
| ⭐⭐ | Insufficient Balance | Transfer fails with proper error | 2h | Low | High |
| ⭐⭐ | KYC Required Error | Non-verified user cannot send | 2h | Low | High |
| ⭐⭐ | Limit Exceeded Error | Daily/monthly limit enforcement | 2h | Medium | High |
| ⭐⭐⭐ | Transaction Rollback | Failed TX rolls back properly | 3h | High | Very High |

### Phase 4: Advanced Features (Week 4) 🚀

| Priority | Test | Description | Time | Complexity | Value |
|----------|------|-------------|------|------------|-------|
| ⭐ | Notification Delivery | Verify emails/SMS sent | 2h | Low | Medium |
| ⭐⭐ | Transaction History | Query past transactions | 2h | Low | Medium |
| ⭐⭐⭐ | Concurrent Transactions | Race condition handling | 3h | High | High |
| ⭐⭐ | Idempotency | Duplicate prevention | 2h | Medium | High |

---

## Next Steps: Wallet Creation Flow

### Current State

```
✅ COMPLETE: User Onboarding Flow
┌────────────────────────────────────────────────────────┐
│  1. User registers → Auth Service                      │
│  2. USER_REGISTERED event → Kafka                      │
│  3. Login → Get JWT token                              │
│  4. Create customer profile → Customer Service         │
└────────────────────────────────────────────────────────┘

⏳ NEXT: Automatic Wallet Creation via Event-Driven Flow
```

### Target State

```
UserOnboardingWithWalletFlowTest
┌────────────────────────────────────────────────────────┐
│  1. User registers → Auth Service                      │
│  2. USER_REGISTERED event → Kafka                      │
│  3. Customer Service listens → Creates Customer        │
│  4. CUSTOMER_CREATED event → Kafka                     │
│  5. Wallet Service listens → Creates Wallet           │
│  6. WALLET_CREATED event → Kafka                       │
│  7. Notification Service → Sends welcome email         │
│  8. Verify wallet exists with correct limits           │
└────────────────────────────────────────────────────────┘
```

### Implementation Checklist

#### 1. Customer Service Event Listener

**File:** `customer-service/src/main/java/com/openwallet/customer/listener/UserEventListener.java`

```java
@Component
@RequiredArgsConstructor
public class UserEventListener {
    
    private final CustomerService customerService;
    private final CustomerEventProducer customerEventProducer;
    
    @KafkaListener(topics = "user-events", groupId = "customer-service")
    public void handleUserRegistered(UserEvent event) {
        if ("USER_REGISTERED".equals(event.getEventType())) {
            log.info("Processing USER_REGISTERED: userId={}", event.getUserId());
            
            // Create customer profile automatically
            Customer customer = customerService.createFromUserEvent(event);
            
            // Publish CUSTOMER_CREATED event
            customerEventProducer.publish(CustomerEvent.builder()
                .customerId(customer.getId())
                .userId(event.getUserId())
                .email(event.getEmail())
                .eventType("CUSTOMER_CREATED")
                .build());
        }
    }
}
```

#### 2. Wallet Service Event Listener

**File:** `wallet-service/src/main/java/com/openwallet/wallet/listener/CustomerEventListener.java`

```java
@Component
@RequiredArgsConstructor
public class CustomerEventListener {
    
    private final WalletService walletService;
    private final WalletEventProducer walletEventProducer;
    
    @KafkaListener(topics = "customer-events", groupId = "wallet-service")
    public void handleCustomerCreated(CustomerEvent event) {
        if ("CUSTOMER_CREATED".equals(event.getEventType())) {
            log.info("Processing CUSTOMER_CREATED: customerId={}", event.getCustomerId());
            
            // Create wallet with initial low limits (KYC pending)
            Wallet wallet = walletService.createWallet(
                event.getCustomerId(),
                BigDecimal.ZERO,                 // Initial balance
                new BigDecimal("5000.00"),       // Low daily limit
                new BigDecimal("20000.00")       // Low monthly limit
            );
            
            // Publish WALLET_CREATED event
            walletEventProducer.publish(WalletEvent.builder()
                .walletId(wallet.getId())
                .customerId(event.getCustomerId())
                .userId(event.getUserId())
                .eventType("WALLET_CREATED")
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build());
        }
    }
}
```

#### 3. Wallet Service Container

**File:** `integration-test/src/test/java/com/openwallet/integration/infrastructure/WalletServiceContainer.java`

```java
public class WalletServiceContainer extends ServiceContainer {
    
    public static final int DEFAULT_PORT = 9003;
    
    public WalletServiceContainer(InfrastructureInfo infrastructure) {
        super("wallet-service", DEFAULT_PORT, infrastructure);
    }
    
    @Override
    protected Class<?> getMainClass() {
        return WalletServiceApplication.class;
    }
    
    @Override
    protected int getPort() {
        return DEFAULT_PORT;
    }
    
    @Override
    public String getServiceName() {
        return "wallet-service";
    }
}
```

#### 4. Update ServiceContainerManager

**File:** `integration-test/src/test/java/com/openwallet/integration/infrastructure/ServiceContainerManager.java`

```java
@Getter
public class ServiceContainerManager {
    
    private final AuthServiceContainer authService;
    private final CustomerServiceContainer customerService;
    private final WalletServiceContainer walletService;  // ADD THIS
    
    private final List<ServiceContainer> containers;
    
    public ServiceContainerManager(InfrastructureInfo infrastructure) {
        this.authService = new AuthServiceContainer(infrastructure);
        this.customerService = new CustomerServiceContainer(infrastructure);
        this.walletService = new WalletServiceContainer(infrastructure);  // ADD THIS
        
        this.containers = List.of(authService, customerService, walletService);  // ADD walletService
    }
    
    // ... rest of the class
}
```

#### 5. Integration Test

**File:** `integration-test/src/test/java/com/openwallet/integration/flows/WalletCreationFlowTest.java`

```java
@DisplayName("Wallet Creation Flow")
public class WalletCreationFlowTest extends IntegrationTestBase {

    private ServiceContainerManager serviceManager;
    private TestHttpClient authClient;
    private TestHttpClient walletClient;
    private KafkaEventVerifier userEventsVerifier;
    private KafkaEventVerifier customerEventsVerifier;
    private KafkaEventVerifier walletEventsVerifier;

    @BeforeEach
    void setUp() {
        log.info("Starting services for wallet creation flow test...");
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();
        
        authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
        walletClient = new TestHttpClient(serviceManager.getWalletService().getBaseUrl());
        
        userEventsVerifier = new KafkaEventVerifier(
            getInfrastructure().getKafkaBootstrapServers(),
            "user-events"
        );
        customerEventsVerifier = new KafkaEventVerifier(
            getInfrastructure().getKafkaBootstrapServers(),
            "customer-events"
        );
        walletEventsVerifier = new KafkaEventVerifier(
            getInfrastructure().getKafkaBootstrapServers(),
            "wallet-events"
        );
        
        log.info("✓ All services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        log.info("Cleaning up test resources...");
        if (userEventsVerifier != null) userEventsVerifier.close();
        if (customerEventsVerifier != null) customerEventsVerifier.close();
        if (walletEventsVerifier != null) walletEventsVerifier.close();
        if (serviceManager != null) serviceManager.stopAll();
    }

    @Test
    @DisplayName("Complete onboarding automatically creates wallet")
    void completeOnboardingCreatesWallet() throws Exception {
        // Step 1: Register user
        log.info("Step 1: Registering new user...");
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", "walletuser_" + System.currentTimeMillis());
        registerRequest.put("email", "walletuser_" + System.currentTimeMillis() + "@example.com");
        registerRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
        assertThat(registerResponse.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> registerBody = authClient.parseJson(registerResponse.getBody());
        String userId = (String) registerBody.get("userId");
        String username = (String) registerBody.get("username");
        
        assertThat(userId).isNotNull();
        log.info("User registered - ID: {}, Username: {}", userId, username);

        // Step 2: Verify USER_REGISTERED event
        log.info("Step 2: Verifying USER_REGISTERED event...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> userEvent = 
            userEventsVerifier.verifyEventContains("userId", userId, 10);
        
        assertThat(userEvent).isNotNull();
        assertThat(userEvent.value()).contains("\"eventType\":\"USER_REGISTERED\"");
        log.info("✓ USER_REGISTERED event verified");

        // Step 3: Verify CUSTOMER_CREATED event
        log.info("Step 3: Verifying CUSTOMER_CREATED event...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> customerEvent = 
            customerEventsVerifier.verifyEventContains("userId", userId, 10);
        
        assertThat(customerEvent).isNotNull();
        assertThat(customerEvent.value()).contains("\"eventType\":\"CUSTOMER_CREATED\"");
        
        Map<String, Object> customerData = authClient.parseJson(customerEvent.value());
        Long customerId = ((Number) customerData.get("customerId")).longValue();
        assertThat(customerId).isNotNull();
        log.info("✓ CUSTOMER_CREATED event verified - Customer ID: {}", customerId);

        // Step 4: Verify WALLET_CREATED event
        log.info("Step 4: Verifying WALLET_CREATED event...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> walletEvent = 
            walletEventsVerifier.verifyEventContains("customerId", customerId.toString(), 10);
        
        assertThat(walletEvent).isNotNull();
        assertThat(walletEvent.value()).contains("\"eventType\":\"WALLET_CREATED\"");
        
        Map<String, Object> walletData = authClient.parseJson(walletEvent.value());
        Long walletId = ((Number) walletData.get("walletId")).longValue();
        assertThat(walletId).isNotNull();
        log.info("✓ WALLET_CREATED event verified - Wallet ID: {}", walletId);

        // Step 5: Login to get JWT token
        log.info("Step 5: Logging in to get access token...");
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);
        
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String accessToken = (String) loginBody.get("accessToken");
        assertThat(accessToken).isNotNull();
        log.info("✓ Login successful, received access token");

        // Step 6: Get wallet details and verify
        log.info("Step 6: Retrieving wallet details...");
        TestHttpClient.HttpResponse walletResponse = walletClient.get(
            "/api/v1/wallets/customer/" + customerId,
            accessToken
        );
        
        assertThat(walletResponse.getStatusCode()).isEqualTo(200);
        
        Map<String, Object> wallet = walletClient.parseJson(walletResponse.getBody());
        assertThat(wallet.get("id")).isEqualTo(walletId.intValue());
        assertThat(wallet.get("customerId")).isEqualTo(customerId.intValue());
        assertThat(wallet.get("balance")).isEqualTo(0);
        assertThat(wallet.get("status")).isEqualTo("ACTIVE");
        assertThat(wallet.get("dailyLimit")).isEqualTo(5000.00);
        assertThat(wallet.get("monthlyLimit")).isEqualTo(20000.00);
        
        log.info("✓ Wallet verified - Balance: {}, Daily Limit: {}, Monthly Limit: {}",
            wallet.get("balance"), wallet.get("dailyLimit"), wallet.get("monthlyLimit"));

        log.info("✓ Complete onboarding with automatic wallet creation successful!");
    }
}
```

---

## Event-Driven Architecture

### Kafka Topics & Events

| Topic | Events | Producer | Consumers |
|-------|--------|----------|-----------|
| **user-events** | USER_REGISTERED | Auth Service | Customer Service |
| **customer-events** | CUSTOMER_CREATED | Customer Service | Wallet Service |
| **kyc-events** | KYC_VERIFIED, KYC_REJECTED | Customer Service | Wallet Service, Notification Service |
| **wallet-events** | WALLET_CREATED, BALANCE_UPDATED | Wallet Service | Notification Service |
| **transaction-events** | TX_INITIATED, TX_COMPLETED, TX_FAILED | Ledger Service | Wallet Service, Notification Service |

### Event Flow Diagram

```
┌──────────────┐
│ Auth Service │
└──────┬───────┘
       │ USER_REGISTERED
       ▼
┌──────────────────┐
│ Customer Service │◄─── Listens to: user-events
└──────┬───────────┘
       │ CUSTOMER_CREATED
       ▼
┌──────────────┐
│Wallet Service│◄─── Listens to: customer-events, kyc-events
└──────┬───────┘
       │ WALLET_CREATED
       ▼
┌──────────────────┐
│Notification Svc  │◄─── Listens to: kyc-events, transaction-events, wallet-events
└──────────────────┘
```

---

## Service Details

### Auth Service (Port 9001)

**Responsibilities:**
- User registration
- User authentication (login)
- JWT token generation
- Password management

**Endpoints:**
- `POST /api/v1/auth/register` → 201 Created
- `POST /api/v1/auth/login` → 200 OK
- `POST /api/v1/auth/refresh` → 200 OK
- `POST /api/v1/auth/logout` → 200 OK

**Events Published:**
- `USER_REGISTERED` (to `user-events`)

**Dependencies:**
- PostgreSQL (user storage)
- Keycloak (user management, JWT)
- Kafka (event publishing)

---

### Customer Service (Port 9002)

**Responsibilities:**
- Customer profile management
- KYC verification workflow
- Customer-user mapping

**Endpoints:**
- `POST /api/v1/customers` → 201 Created
- `GET /api/v1/customers/{id}` → 200 OK
- `POST /api/v1/customers/{id}/kyc` → 200 OK
- `GET /api/v1/customers/{id}/kyc/status` → 200 OK

**Events Published:**
- `CUSTOMER_CREATED` (to `customer-events`)
- `KYC_VERIFIED` (to `kyc-events`)
- `KYC_REJECTED` (to `kyc-events`)

**Events Consumed:**
- `USER_REGISTERED` (from `user-events`) ⏳ TO BE IMPLEMENTED

**Dependencies:**
- PostgreSQL (customer, KYC storage)
- Kafka (event pub/sub)

---

### Wallet Service (Port 9003)

**Responsibilities:**
- Wallet creation and management
- Balance tracking
- Transaction limits (daily/monthly)
- KYC-based limit enforcement

**Endpoints:**
- `POST /api/v1/wallets` → 201 Created
- `GET /api/v1/wallets/{id}` → 200 OK
- `GET /api/v1/wallets/customer/{customerId}` → 200 OK
- `PUT /api/v1/wallets/{id}/limits` → 200 OK

**Events Published:**
- `WALLET_CREATED` (to `wallet-events`) ⏳ TO BE IMPLEMENTED
- `BALANCE_UPDATED` (to `wallet-events`) ⏳ TO BE IMPLEMENTED

**Events Consumed:**
- `CUSTOMER_CREATED` (from `customer-events`) ⏳ TO BE IMPLEMENTED
- `KYC_VERIFIED` (from `kyc-events`) ⏳ TO BE IMPLEMENTED
- `TRANSACTION_COMPLETED` (from `transaction-events`) ⏳ TO BE IMPLEMENTED

**Dependencies:**
- PostgreSQL (wallet storage)
- Kafka (event pub/sub)

---

### Ledger Service (Port 9004)

**Responsibilities:**
- Double-entry bookkeeping
- Transaction recording (deposit, withdrawal, transfer)
- Immutable audit trail
- Idempotency enforcement

**Endpoints:**
- `POST /api/v1/transactions/deposit` → 201 Created
- `POST /api/v1/transactions/withdrawal` → 201 Created
- `POST /api/v1/transactions/transfer` → 201 Created
- `GET /api/v1/transactions/{id}` → 200 OK

**Events Published:**
- `TRANSACTION_INITIATED` (to `transaction-events`)
- `TRANSACTION_COMPLETED` (to `transaction-events`)
- `TRANSACTION_FAILED` (to `transaction-events`)

**Events Consumed:**
- None (receives direct HTTP requests)

**Dependencies:**
- PostgreSQL (transaction, ledger entry storage)
- Kafka (event publishing)

---

### Notification Service (Port 9005)

**Responsibilities:**
- Email notifications
- SMS notifications
- Notification history
- Event-driven alerts

**Endpoints:**
- `POST /api/v1/notifications` → 201 Created
- `GET /api/v1/notifications/{id}` → 200 OK

**Events Published:**
- None (terminal consumer)

**Events Consumed:**
- `KYC_VERIFIED` (from `kyc-events`) ✅ IMPLEMENTED
- `KYC_REJECTED` (from `kyc-events`) ✅ IMPLEMENTED
- `TRANSACTION_COMPLETED` (from `transaction-events`) ✅ IMPLEMENTED
- `TRANSACTION_FAILED` (from `transaction-events`) ✅ IMPLEMENTED

**Dependencies:**
- PostgreSQL (notification storage)
- Kafka (event consuming)
- Email provider (future: SendGrid, AWS SES)
- SMS provider (future: Twilio, AWS SNS)

---

## KYC & Wallet Limits Strategy

### Approach: KYC-Based Limits (No Tiers)

We use **KYC status directly** to determine wallet capabilities:

| KYC Status | Can Receive | Can Send | Can Withdraw | Daily Limit | Monthly Limit |
|------------|-------------|----------|--------------|-------------|---------------|
| **PENDING** | ✅ Yes | ❌ No | ❌ No | 5,000 | 20,000 |
| **IN_PROGRESS** | ✅ Yes | ❌ No | ❌ No | 5,000 | 20,000 |
| **VERIFIED** | ✅ Yes | ✅ Yes | ✅ Yes | 100,000 | 1,000,000 |
| **REJECTED** | ❌ No | ❌ No | ❌ No | 0 | 0 |

### Benefits of This Approach

1. ✅ **No Schema Changes** - Uses existing `KycStatus` enum
2. ✅ **No Tier Management** - Directly maps KYC status to limits
3. ✅ **Simple Logic** - Easy to understand and maintain
4. ✅ **Event-Driven** - KYC_VERIFIED automatically upgrades limits
5. ✅ **Fast to Implement** - Minimal code changes required

### Implementation

```java
// wallet-service
@Service
public class WalletLimitsService {
    
    public WalletLimits getLimitsForKycStatus(KycStatus kycStatus) {
        return switch (kycStatus) {
            case PENDING, IN_PROGRESS -> WalletLimits.builder()
                .canReceive(true)
                .canSend(false)
                .canWithdraw(false)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("20000.00"))
                .build();
                
            case VERIFIED -> WalletLimits.builder()
                .canReceive(true)
                .canSend(true)
                .canWithdraw(true)
                .dailyLimit(new BigDecimal("100000.00"))
                .monthlyLimit(new BigDecimal("1000000.00"))
                .build();
                
            case REJECTED -> WalletLimits.builder()
                .canReceive(false)
                .canSend(false)
                .canWithdraw(false)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .build();
        };
    }
}
```

---

## Testing Guidelines

### Integration Test Best Practices

1. **Always verify the complete event chain**
   - Don't just check the final state
   - Verify each Kafka event was published correctly
   - Verify each service reacted to events

2. **Use unique identifiers for each test**
   - Use `System.currentTimeMillis()` in usernames/emails
   - Prevents test interference

3. **Clean up resources after tests**
   - Close Kafka event verifiers
   - Stop service containers
   - Don't rely on `@AfterAll` - use `@AfterEach` for safety

4. **Wait for asynchronous events**
   - Use reasonable timeouts (10 seconds for events)
   - Log what you're waiting for
   - Fail fast with clear error messages

5. **Test both happy path and error cases**
   - Happy: Everything works as expected
   - Error: Invalid data, insufficient balance, KYC required, etc.

---

## Next Actions

### Immediate (This Week)

- [ ] Create `WalletServiceContainer` class
- [ ] Update `ServiceContainerManager` to include wallet service
- [ ] Create `UserEventListener` in customer-service
- [ ] Create `CustomerEventListener` in wallet-service
- [ ] Create `WalletCreationFlowTest` integration test
- [ ] Run and verify complete flow works

### Short Term (Next Week)

- [ ] Implement KYC verification flow test
- [ ] Add KYC-based limit enforcement
- [ ] Create deposit flow test
- [ ] Add wallet balance update logic

### Medium Term (Weeks 3-4)

- [ ] P2P transfer flow
- [ ] Withdrawal flow
- [ ] Error handling tests
- [ ] Notification delivery tests

---

## Resources

### Documentation

- [Integration Test README](./README.md)
- [User Onboarding Flow Analysis](./USER_ONBOARDING_FLOW_ANALYSIS.md)
- [Keycloak Configuration](./KEYCLOAK_CONFIGURATION.md)
- [Pre-Flight Checklist](./PRE_FLIGHT_CHECKLIST.md)

### Commands

```bash
# Run all integration tests
mvn test -pl integration-test

# Run specific test
mvn test -Dtest=WalletCreationFlowTest -pl integration-test

# Run with verbose logging
mvn test -Dtest=WalletCreationFlowTest -pl integration-test -X

# Build all services
mvn clean install -DskipTests

# Stop all TestContainers
docker stop $(docker ps -q --filter "label=org.testcontainers=true")
```

---

## Glossary

- **KYC**: Know Your Customer - Identity verification process
- **P2P**: Peer-to-Peer - Direct transfer between users
- **Double-Entry**: Accounting system where every transaction has equal debits and credits
- **Idempotency**: Ability to safely retry operations without side effects
- **Event-Driven**: Architecture where services communicate via events
- **TestContainers**: Library for running Docker containers in tests

---

**Last Updated:** December 14, 2025  
**Status:** User Onboarding Complete ✅  
**Next Milestone:** Automatic Wallet Creation ⏳

