# Integration Tests

Fast integration tests using embedded Spring Boot services with TestContainers infrastructure.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    IntegrationTestBase                       │
│  - Manages TestContainers (PostgreSQL, Kafka, Keycloak)    │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              ServiceContainerManager                         │
│  - Orchestrates all microservice containers                 │
│  - Provides centralized lifecycle management                │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Auth       │ │  Customer    │ │   Future     │
│   Service    │ │  Service     │ │   Services   │
│  Container   │ │  Container   │ │  Container   │
└──────────────┘ └──────────────┘ └──────────────┘
```

## Components

### 1. **Infrastructure Layer**

#### `InfrastructureManager`
- Manages TestContainers for PostgreSQL, Kafka, and Keycloak
- Provides dynamic connection URLs for tests
- Ensures containers are reused across tests for speed

#### `IntegrationTestBase`
- Base class for all integration tests
- Starts infrastructure in `@BeforeAll`
- Provides helper methods for infrastructure access

### 2. **Service Container Layer**

#### `ServiceContainer` (Abstract)
- Base class for all service containers
- Manages service lifecycle (start/stop)
- Wraps `EmbeddedServiceRunner` for cleaner API

#### `AuthServiceContainer`
- Container for auth-service
- Default port: 9001

#### `CustomerServiceContainer`
- Container for customer-service
- Default port: 9002

#### `ServiceContainerManager`
- Orchestrates all service containers
- Provides `startAll()`, `stopAll()` methods
- Gives centralized access to all services
- Reports service status

### 3. **Test Execution Layer**

#### `EmbeddedServiceRunner`
- Runs Spring Boot services in the same JVM (not Docker)
- Configures services with TestContainers URLs
- Overrides profile configurations via command-line args
- Waits for health checks before proceeding

## Benefits

### ✅ **Fast Startup**
- **10-20 seconds** vs 2-5 minutes with Docker
- Services run in same JVM
- No image builds required

### ✅ **Easy Debugging**
- Direct access to service logs
- Standard Java debugging works
- Breakpoints in service code

### ✅ **Production-Like**
- Real HTTP communication
- Real database (PostgreSQL)
- Real message broker (Kafka)
- Real auth server (Keycloak)

### ✅ **Clean Architecture**
- Service containers encapsulate service details
- Manager provides simple API
- Easy to add new services

## Usage

### Basic Test Example

```java
@DisplayName("My Integration Test")
public class MyIntegrationTest extends IntegrationTestBase {

    private static ServiceContainerManager serviceManager;

    @BeforeAll
    static void startServices() {
        // Initialize and start all services
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();
    }

    @AfterAll
    static void stopServices() {
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
    }

    @Test
    void myTest() {
        // Access services
        String authUrl = serviceManager.getAuthService().getBaseUrl();
        String customerUrl = serviceManager.getCustomerService().getBaseUrl();
        
        // Make HTTP calls, test interactions, etc.
    }
}
```

### Starting Specific Services

```java
// Start only auth service
serviceManager.start(serviceManager.getAuthService());

// Start multiple services
serviceManager.start(
    serviceManager.getAuthService(),
    serviceManager.getCustomerService()
);
```

### Checking Service Status

```java
// Check if all services are running
boolean allUp = serviceManager.allRunning();

// Get detailed status
String status = serviceManager.getStatus();
log.info(status);
// Output:
// Service Status:
//   - auth-service: RUNNING (port 9001)
//   - customer-service: RUNNING (port 9002)
```

## Adding a New Service

1. **Create Service Container**

```java
public class PaymentServiceContainer extends ServiceContainer {
    
    public static final int DEFAULT_PORT = 9003;
    
    public PaymentServiceContainer(InfrastructureInfo infrastructure) {
        super("payment-service", DEFAULT_PORT, infrastructure);
    }
    
    @Override
    protected Class<?> getMainClass() {
        return PaymentServiceApplication.class;
    }
}
```

2. **Register in ServiceContainerManager**

```java
@Getter
public class ServiceContainerManager {
    
    private PaymentServiceContainer paymentService;
    
    private void initializeContainers() {
        authService = new AuthServiceContainer(infrastructure);
        customerService = new CustomerServiceContainer(infrastructure);
        paymentService = new PaymentServiceContainer(infrastructure); // Add this
        
        containers.add(authService);
        containers.add(customerService);
        containers.add(paymentService); // Add this
    }
}
```

3. **Use in Tests**

```java
String paymentUrl = serviceManager.getPaymentService().getBaseUrl();
```

## Configuration

### Command-Line Args (Highest Precedence)

The `EmbeddedServiceRunner` overrides service configurations via command-line args:

- `spring.datasource.url` → TestContainers PostgreSQL URL
- `spring.flyway.enabled` → false (disabled)
- `spring.jpa.hibernate.ddl-auto` → update (auto-create tables)
- `management.health.redis.enabled` → false (disabled)
- `management.endpoint.health.group.readiness.include` → db,diskSpace,ping

This ensures services use test infrastructure instead of local configs.

## Running Tests

### IDE (IntelliJ IDEA)
Right-click on test class → Run

### Maven
```bash
mvn test -Dtest=ServiceStartupProofTest
```

### All Integration Tests
```bash
mvn test -pl integration-test
```

## Troubleshooting

### Services Don't Start
- Check if ports 9001, 9002 are available
- Review service logs for errors
- Ensure Docker is running (for TestContainers)

### Health Check Fails
- Increase timeout in `EmbeddedServiceRunner.waitForHealth()`
- Check which health indicator is failing (logs show response)
- Verify infrastructure containers are running

### Database Issues
- Ensure PostgreSQL TestContainer is up
- Check connection URL in logs
- Verify `ddl-auto=update` is set (auto-creates tables)

## Performance Tips

1. **Reuse Infrastructure** - TestContainers stay running across test classes
2. **Parallel Tests** - Run test classes in parallel (but services in sequence)
3. **Selective Services** - Start only services needed for specific tests
4. **Container Reuse** - Enable TestContainers reuse flag (already enabled)

## Future Enhancements

- [ ] Service dependency management (start order)
- [ ] Parallel service startup
- [ ] Health check customization per service
- [ ] Integration with Testcontainers Cloud
- [ ] Performance metrics collection
