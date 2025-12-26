# Integration Test Optimization Guide

This guide explains the new optimized test infrastructure that reduces test execution time by:
- Starting only required services
- Pre-creating test users
- Fast-fail validation
- Simplified test setup

## Overview

The optimization framework consists of:

1. **ServiceRequirement Annotation** - Declares which services a test needs
2. **ServiceContainerManager** - Enhanced with selective startup
3. **TestUserManager** - Pre-creates and manages test users
4. **TestDataValidator** - Fast-fail validation utilities
5. **OptimizedTestHelper** - Convenience wrapper for all features

## Quick Start

### Basic Usage

```java
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class MyTest extends IntegrationTestBase {
    
    private OptimizedTestHelper testHelper;
    private TestUserManager userManager;
    private TestHttpClient customerClient;
    
    @BeforeEach
    void setUp() {
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this); // Only starts AUTH and CUSTOMER
        
        // Validate services are running (fast-fail)
        testHelper.validateServices(
            ServiceRequirement.ServiceType.AUTH,
            ServiceRequirement.ServiceType.CUSTOMER
        );
        
        // Get clients
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        userManager = testHelper.getUserManager();
        
        // Create test users before tests run
        createTestUsers();
    }
    
    @AfterEach
    void tearDown() {
        testHelper.cleanup(); // Stops only started services
    }
    
    private void createTestUsers() {
        userManager.createUser("testuser", "test@example.com");
    }
    
    @Test
    void myTest() {
        // Fast-fail validation
        TestDataValidator.requireUserExists(userManager, "testuser");
        String token = userManager.getToken("testuser");
        TestDataValidator.requireNotNull(token, "Access token");
        
        // Use token in test...
    }
}
```

## Service Selection

### Using @ServiceRequirement Annotation

```java
// Only start AUTH and CUSTOMER services
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class CustomerTest extends IntegrationTestBase {
    // Wallet service will NOT be started
}

// Start all services (default behavior)
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER, ServiceRequirement.ServiceType.WALLET})
public class FullFlowTest extends IntegrationTestBase {
    // All services started
}

// No annotation = all services (backward compatibility)
public class LegacyTest extends IntegrationTestBase {
    // All services started
}
```

### Method-Level Service Requirements

```java
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class MyTest extends IntegrationTestBase {
    
    @Test
    @ServiceRequirement({ServiceRequirement.ServiceType.AUTH}) // Override for this method
    void authOnlyTest() {
        // Only AUTH service started for this test
    }
}
```

## Test User Management

### Pre-Creating Users

```java
@BeforeEach
void setUp() {
    testHelper = new OptimizedTestHelper(getInfrastructure());
    testHelper.startRequiredServices(this);
    userManager = testHelper.getUserManager();
    
    // Create users before tests run
    createTestUsers();
}

private void createTestUsers() {
    // Create users with unique names to avoid conflicts
    String timestamp = String.valueOf(System.currentTimeMillis());
    
    userManager.createUser("user1_" + timestamp, "user1_" + timestamp + "@test.com");
    userManager.createUser("user2_" + timestamp, "user2_" + timestamp + "@test.com");
}

@Test
void myTest() {
    // Use pre-created user
    String token = userManager.getToken("user1_" + timestamp);
    // No registration/login needed during test!
}
```

### User Creation Options

```java
// Default password
userManager.createUser("username", "email@test.com");

// Custom password
userManager.createUser("username", "email@test.com", "CustomPassword123!");

// Check if user exists
if (userManager.hasUser("username")) {
    // User already exists
}

// Get user details
TestUserManager.TestUser user = userManager.getUser("username");
String userId = user.getUserId();
String token = user.getAccessToken();
```

## Fast-Fail Validation

### Service Validation

```java
// Validate single service
TestDataValidator.requireServiceRunning(serviceManager, ServiceRequirement.ServiceType.AUTH);

// Validate multiple services
TestDataValidator.requireServicesRunning(serviceManager, 
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER
);
```

### User Validation

```java
// Validate user exists
TestDataValidator.requireUserExists(userManager, "username");

// Validate token is not null
String token = userManager.getToken("username");
TestDataValidator.requireNotNull(token, "Access token");
```

### HTTP Response Validation

```java
TestHttpClient.HttpResponse response = client.get("/api/v1/endpoint", token);
TestDataValidator.requireSuccess(response, "Get endpoint");
// Fails immediately if status code is not 200-299
```

## Migration Guide

### Before (Old Pattern)

```java
public class OldTest extends IntegrationTestBase {
    private ServiceContainerManager serviceManager;
    
    @BeforeEach
    void setUp() {
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll(); // Starts ALL services
    }
    
    @Test
    void test() {
        // Register user during test
        String[] credentials = registerAndLogin("testuser");
        String token = credentials[0];
        // Test continues...
    }
}
```

### After (Optimized Pattern)

```java
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class NewTest extends IntegrationTestBase {
    private OptimizedTestHelper testHelper;
    private TestUserManager userManager;
    
    @BeforeEach
    void setUp() {
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this); // Only required services
        userManager = testHelper.getUserManager();
        createTestUsers(); // Pre-create users
    }
    
    @Test
    void test() {
        // Use pre-created user
        String token = userManager.getToken("testuser");
        // Test continues...
    }
}
```

## Benefits

### Performance Improvements

- **Faster Startup**: Only required services start (e.g., 2 services instead of 3 = ~33% faster)
- **Faster Tests**: No user registration/login during test execution
- **Reduced Resource Usage**: Unused services don't consume memory/CPU

### Reliability Improvements

- **Fast-Fail Validation**: Tests fail immediately when preconditions aren't met
- **Clear Error Messages**: Validation errors are descriptive and actionable
- **Consistent Setup**: Pre-created users ensure consistent test state

### Maintainability Improvements

- **Simpler Tests**: Less boilerplate code
- **Clear Dependencies**: @ServiceRequirement makes dependencies explicit
- **Easier Debugging**: Validation failures point to exact issues

## Example: Complete Optimized Test

See `CustomerProfileCrudTestOptimized.java` for a complete example demonstrating:
- Selective service startup
- Pre-created test users
- Fast-fail validation
- Clean test structure

## Backward Compatibility

All existing tests continue to work without changes. The optimization features are opt-in:
- Tests without `@ServiceRequirement` start all services (old behavior)
- Tests can still register users during execution (old pattern)
- No breaking changes to existing APIs

## Next Steps

1. **Identify Service Dependencies**: For each test class, determine which services are actually needed
2. **Add @ServiceRequirement**: Annotate test classes with required services
3. **Pre-Create Users**: Move user creation to `@BeforeEach` setup
4. **Add Validation**: Use `TestDataValidator` for fast-fail checks
5. **Measure Improvements**: Compare test execution times before/after

## Troubleshooting

### Service Not Running

```
Error: Required service AUTH is not running
```

**Solution**: Ensure `testHelper.startRequiredServices(this)` is called in `@BeforeEach`

### User Not Found

```
Error: Required test user 'testuser' does not exist
```

**Solution**: Ensure user is created in `createTestUsers()` method before test runs

### Token is Null

```
Error: Required value 'Access token' is null
```

**Solution**: Check that user was successfully created and logged in. Verify Keycloak is running.

