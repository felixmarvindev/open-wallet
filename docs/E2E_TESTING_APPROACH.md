# End-to-End Testing Strategy for OpenWallet Microservices

## Overview

This document outlines different approaches for creating an end-to-end testing service that can start all microservices and test inter-service communication.

## Problem Statement

We need to test:
- **Complete user flows** across multiple services (Register → Create Customer → Create Wallet → Make Transaction)
- **Inter-service communication** via REST APIs and Kafka events
- **Real infrastructure** (PostgreSQL, Redis, Kafka, Keycloak) behavior
- **JWT token propagation** across services
- **Event-driven flows** (e.g., transaction events triggering notifications)

---

## Approach 1: TestContainers-Based Integration Test Module (RECOMMENDED) ⭐

### Concept
Create a dedicated `integration-test` module that:
- Uses **TestContainers** to spin up real infrastructure (PostgreSQL, Kafka, Keycloak, Redis)
- Starts all microservices as **Spring Boot applications** in test mode
- Runs **end-to-end tests** that make actual HTTP calls between services
- Verifies **Kafka events** are published and consumed correctly

### Architecture

```
integration-test/
├── pom.xml
├── src/
│   └── test/
│       └── java/
│           └── com/openwallet/integration/
│               ├── IntegrationTestBase.java          # Base class with TestContainers setup
│               ├── InfrastructureManager.java        # Manages container lifecycle
│               ├── ServiceManager.java               # Starts/stops microservices
│               ├── flows/
│               │   ├── UserOnboardingFlowTest.java  # Register → Create Customer
│               │   ├── WalletCreationFlowTest.java   # Create Wallet → Fund Wallet
│               │   ├── TransactionFlowTest.java     # Transfer → Verify Balance
│               │   └── EventDrivenFlowTest.java      # Transaction → Notification
│               └── utils/
│                   ├── TestRestClient.java           # HTTP client for services
│                   ├── KafkaTestUtils.java          # Kafka consumer for verification
│                   └── KeycloakTestUtils.java        # Keycloak setup/cleanup
```

### Pros
✅ **Fully isolated** - Each test run gets fresh containers  
✅ **Real infrastructure** - Tests against actual PostgreSQL, Kafka, Keycloak  
✅ **CI/CD friendly** - Works in GitHub Actions, Jenkins, etc.  
✅ **Fast startup** - Containers start in parallel  
✅ **Reproducible** - Same environment every time  
✅ **No manual setup** - Everything is automated  

### Cons
⚠️ **Requires Docker** - Must have Docker installed  
⚠️ **Slower than unit tests** - Takes 30-60 seconds to start containers  
⚠️ **Resource intensive** - Needs CPU/memory for containers  

### Implementation Steps

1. **Create `integration-test` module**
   - Add as child module in parent `pom.xml`
   - Depend on all service modules (for DTOs, shared code)

2. **Add TestContainers dependencies**
   ```xml
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>testcontainers</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>postgresql</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>kafka</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>keycloak</artifactId>
       <scope>test</scope>
   </dependency>
   ```

3. **Create InfrastructureManager**
   - Manages PostgreSQL, Kafka, Keycloak containers
   - Provides connection URLs to services
   - Handles container lifecycle (start/stop)

4. **Create ServiceManager**
   - Starts each microservice as a Spring Boot application
   - Configures services to connect to TestContainers infrastructure
   - Waits for services to be healthy before tests run

5. **Create Test Flows**
   - Use `RestTemplate` or `WebClient` to make HTTP calls
   - Verify responses and Kafka events
   - Clean up test data between tests

### Example Test Flow

```java
@SpringBootTest
class UserOnboardingFlowTest extends IntegrationTestBase {
    
    @Test
    void completeUserOnboardingFlow() {
        // 1. Register user via Auth Service
        RegisterResponse registerResponse = authClient.register(
            "testuser", "test@example.com", "Password123!"
        );
        String userId = registerResponse.getUserId();
        
        // 2. Login and get JWT token
        LoginResponse loginResponse = authClient.login(
            "testuser", "Password123!"
        );
        String accessToken = loginResponse.getAccessToken();
        
        // 3. Create customer via Customer Service
        CustomerResponse customer = customerClient.createCustomer(
            accessToken, CreateCustomerRequest.builder()...
        );
        
        // 4. Verify Kafka event was published
        UserEvent event = kafkaTestUtils.consumeUserEvent();
        assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
        
        // 5. Create wallet via Wallet Service
        WalletResponse wallet = walletClient.createWallet(accessToken, userId);
        
        // 6. Verify wallet was created in database
        assertThat(walletRepository.findByUserId(userId)).isPresent();
    }
}
```

---

## Approach 2: Docker Compose + Test Runner

### Concept
- Use existing `docker-compose.yml` to start infrastructure
- Build and start all microservices as Docker containers
- Run tests that make HTTP calls to running services
- Use a test runner script (Python/Node.js) to orchestrate

### Pros
✅ **Uses existing setup** - Leverages docker-compose.yml  
✅ **Real services** - Tests actual deployed services  
✅ **Easy to debug** - Can inspect running containers  

### Cons
⚠️ **Manual orchestration** - Need scripts to start/stop services  
⚠️ **Port conflicts** - Must manage ports carefully  
⚠️ **Slower** - Must build Docker images first  
⚠️ **Less isolated** - Shared state between test runs  

### Implementation
- Create `docker-compose.test.yml` with all services
- Write test runner script:
  ```bash
  #!/bin/bash
  docker-compose -f docker-compose.test.yml up -d
  wait_for_services  # Wait for all services to be healthy
  mvn test -Dtest=IntegrationTestSuite
  docker-compose -f docker-compose.test.yml down
  ```

---

## Approach 3: Spring Cloud Contract Testing

### Concept
- Define **contracts** between services (API contracts)
- Generate **stubs** for each service
- Test services in isolation using stubs
- Verify contracts are met

### Pros
✅ **Fast** - No need to start all services  
✅ **Contract verification** - Ensures API compatibility  
✅ **Consumer-driven** - Services define what they need  

### Cons
⚠️ **Doesn't test real integration** - Uses stubs, not real services  
⚠️ **Complex setup** - Requires contract definitions  
⚠️ **Doesn't test Kafka events** - Focuses on REST APIs  

### Best For
- API contract verification
- Service versioning
- **Not ideal for full E2E testing**

---

## Approach 4: Hybrid Approach (Recommended for Production)

### Concept
Combine multiple approaches:
- **TestContainers** for infrastructure (PostgreSQL, Kafka, Keycloak)
- **Embedded services** for some services (faster)
- **Real services** for critical paths (auth, customer)
- **Contract testing** for API compatibility

### Pros
✅ **Best of all worlds** - Fast + comprehensive  
✅ **Flexible** - Can test different scenarios  
✅ **Production-like** - Tests real service interactions  

---

## Recommendation: Approach 1 (TestContainers)

**Why?**
1. **Fully automated** - No manual setup required
2. **CI/CD ready** - Works in any environment with Docker
3. **Isolated** - Each test run is independent
4. **Real infrastructure** - Tests actual PostgreSQL, Kafka, Keycloak
5. **Maintainable** - All test code in one module

### Implementation Priority

**Phase 1: Basic Setup**
- [ ] Create `integration-test` module
- [ ] Set up TestContainers for PostgreSQL, Kafka, Keycloak
- [ ] Create base test class with infrastructure setup

**Phase 2: Service Management**
- [ ] Create ServiceManager to start/stop microservices
- [ ] Add health check waiting logic
- [ ] Configure services to use TestContainers infrastructure

**Phase 3: Test Flows**
- [ ] User onboarding flow (Register → Create Customer)
- [ ] Wallet creation flow (Create Wallet → Fund Wallet)
- [ ] Transaction flow (Transfer → Verify Balance)
- [ ] Event-driven flow (Transaction → Notification)

**Phase 4: Advanced Features**
- [ ] Parallel test execution
- [ ] Test data cleanup utilities
- [ ] Performance testing scenarios
- [ ] Failure scenario testing (service down, network issues)

---

## Example Test Scenarios

### Scenario 1: Complete User Onboarding
```
1. Register user → Auth Service
2. Login → Get JWT token
3. Create customer profile → Customer Service
4. Verify USER_REGISTERED event published → Kafka
5. Create wallet → Wallet Service
6. Verify wallet created in database
```

### Scenario 2: Transaction Flow
```
1. User A creates wallet and funds it (KES 1000)
2. User B creates wallet
3. User A transfers KES 500 to User B
4. Verify:
   - User A balance = KES 500
   - User B balance = KES 500
   - Transaction event published → Kafka
   - Notification sent to User B → Notification Service
   - Ledger entries created (double-entry)
```

### Scenario 3: Event-Driven Flow
```
1. Customer completes KYC verification
2. Verify KYC_APPROVED event published
3. Verify wallet is automatically activated
4. Verify notification sent to customer
```

---

## Next Steps

1. **Review this document** and choose approach
2. **Create integration-test module** structure
3. **Implement TestContainers setup** for infrastructure
4. **Create first E2E test flow** (User Onboarding)
5. **Run tests in CI/CD** pipeline

---

## Questions to Consider

1. **Do you want to test with real Keycloak or mock it?**
   - Real Keycloak: More realistic, but slower
   - Mock Keycloak: Faster, but less realistic

2. **How fast do tests need to be?**
   - TestContainers: ~30-60 seconds startup
   - Embedded services: ~5-10 seconds startup

3. **Do you need to test failure scenarios?**
   - Service down
   - Network partitions
   - Database failures

4. **Should tests run in parallel?**
   - Requires careful port management
   - Faster overall execution

---

## References

- [TestContainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Kafka TestContainers](https://www.testcontainers.org/modules/kafka/)
- [Keycloak TestContainers](https://www.testcontainers.org/modules/keycloak/)

