# Integration Test Module

This module contains end-to-end integration tests for the OpenWallet microservices platform.

## Overview

The integration tests use **TestContainers** to spin up real infrastructure (PostgreSQL, Kafka, Keycloak) and test complete flows across multiple microservices.

## Prerequisites

- **Docker** must be installed and running
- **Java 17+**
- **Maven 3.8+**

## Architecture

```
Integration Test Module
├── InfrastructureManager      # Manages TestContainers lifecycle
├── IntegrationTestBase         # Base class for all integration tests
├── flows/                      # End-to-end test flows
│   ├── UserOnboardingFlowTest
│   ├── WalletCreationFlowTest
│   └── TransactionFlowTest
└── utils/                      # Test utilities
    ├── TestRestClient
    ├── KafkaTestUtils
    └── KeycloakTestUtils
```

## Running Tests

### Run All Integration Tests
```bash
mvn test -pl integration-test
```

### Run Specific Test Class
```bash
mvn test -pl integration-test -Dtest=UserOnboardingFlowTest
```

### Run from Root Directory
```bash
mvn test -pl integration-test
```

## Infrastructure

The tests automatically start:
- **PostgreSQL 15** - Database for all services
- **Kafka 7.5.0** - Event streaming platform
- **Keycloak 23.0.0** - Identity and access management

Containers are reused across test classes for performance (using `withReuse(true)`).

## Writing Tests

### Basic Test Structure

```java
@SpringBootTest
class MyIntegrationTest extends IntegrationTestBase {
    
    @Test
    void myTest() {
        // Infrastructure is already started
        // Use getInfrastructure() to access container details
        String postgresUrl = getPostgresUrl();
        String kafkaServers = getKafkaBootstrapServers();
        
        // Your test code here
    }
}
```

### Accessing Infrastructure

```java
// Get infrastructure manager
InfrastructureManager infra = getInfrastructure();

// Get connection details
String postgresUrl = getPostgresUrl();
String kafkaServers = getKafkaBootstrapServers();
String keycloakUrl = getKeycloakBaseUrl();
```

## Test Flows

### Phase 1: Basic Setup ✅
- [x] Create integration-test module
- [x] Set up TestContainers for PostgreSQL, Kafka, Keycloak
- [x] Create base test class with infrastructure setup

### Phase 2: Service Management (Next)
- [ ] Create ServiceManager to start/stop microservices
- [ ] Add health check waiting logic
- [ ] Configure services to use TestContainers infrastructure

### Phase 3: Test Flows (Future)
- [ ] User onboarding flow (Register → Create Customer)
- [ ] Wallet creation flow (Create Wallet → Fund Wallet)
- [ ] Transaction flow (Transfer → Verify Balance)
- [ ] Event-driven flow (Transaction → Notification)

## Troubleshooting

### Docker Not Running
```
Error: Could not find a valid Docker environment
```
**Solution**: Ensure Docker Desktop (or Docker daemon) is running.

### Port Conflicts
```
Error: Port already in use
```
**Solution**: TestContainers uses random ports by default. If conflicts occur, check for other running containers.

### Container Startup Timeout
```
Error: Container startup timeout
```
**Solution**: Increase timeout in `InfrastructureManager` or check Docker resources (CPU/Memory).

## Notes

- Containers are started once and reused across test classes for performance
- Each test class gets a fresh database (if using `@Transactional` or manual cleanup)
- Kafka topics are created automatically
- Keycloak realm must be configured manually (see `docs/KEYCLOAK_SETUP.md`)


