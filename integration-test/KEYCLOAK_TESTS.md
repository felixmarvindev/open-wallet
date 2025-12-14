# Keycloak Configuration Tests

## 📋 Overview

Comprehensive tests to verify Keycloak is properly configured for integration testing.

**Test File**: `KeycloakConfigurationTest.java`

---

## ✅ Tests Included

### **1. Keycloak Server Accessibility**
```java
@Test
@DisplayName("Keycloak server is accessible")
void keycloakIsAccessible()
```

**Verifies:**
- ✅ Keycloak container is running
- ✅ HTTP endpoint is accessible
- ✅ Server responds to requests

**Expected Result**: 200 OK from base URL

---

### **2. Realm Existence**
```java
@Test
@DisplayName("Realm 'openwallet' exists")
void realmExists()
```

**Verifies:**
- ✅ Realm `openwallet` was imported
- ✅ Realm metadata is accessible
- ✅ Public key is available for JWT validation

**Checks:**
```bash
GET /realms/openwallet

Response:
{
  "realm": "openwallet",
  "public_key": "...",
  "token-service": "..."
}
```

---

### **3. OpenID Connect Configuration**
```java
@Test
@DisplayName("OpenID Connect configuration is available")
void openIdConfigurationExists()
```

**Verifies:**
- ✅ OIDC discovery endpoint works
- ✅ All required endpoints are configured:
  - Authorization endpoint
  - Token endpoint
  - JWKS URI
  - Issuer

**Checks:**
```bash
GET /realms/openwallet/.well-known/openid-configuration

Response:
{
  "issuer": "http://localhost:8080/realms/openwallet",
  "authorization_endpoint": "...",
  "token_endpoint": "...",
  "jwks_uri": "...",
  ...
}
```

---

### **4. Test User Authentication**
```java
@Test
@DisplayName("Test user 'testuser' can authenticate")
void testUserCanAuthenticate()
```

**Verifies:**
- ✅ Test user `testuser/testpass` exists
- ✅ Password authentication works
- ✅ Access token is issued
- ✅ Refresh token is issued
- ✅ Token expires in 3600 seconds (1 hour)

**Request:**
```http
POST /realms/openwallet/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=auth-service
&client_secret=8hngZZARbVBbrnwgF6KG5KWbsPvWfAez
&username=testuser
&password=testpass
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

---

### **5. Admin User Authentication**
```java
@Test
@DisplayName("Admin user 'admin' can authenticate")
void adminUserCanAuthenticate()
```

**Verifies:**
- ✅ Admin user `admin/admin` exists
- ✅ Admin can authenticate
- ✅ Admin receives valid tokens

---

### **6. JWT Token Claims**
```java
@Test
@DisplayName("JWT token contains correct claims")
void jwtTokenContainsCorrectClaims()
```

**Verifies JWT contains:**
- ✅ `sub` - User ID (subject)
- ✅ `preferred_username` - Username
- ✅ `email` - User email
- ✅ `realm_access.roles` - User roles array
- ✅ `iss` - Issuer (Keycloak realm)
- ✅ `exp` - Expiration time
- ✅ `iat` - Issued at time

**Decoded JWT Payload:**
```json
{
  "sub": "user-uuid",
  "preferred_username": "testuser",
  "email": "testuser@openwallet.com",
  "realm_access": {
    "roles": ["USER", "CUSTOMER"]
  },
  "iss": "http://localhost:8080/realms/openwallet",
  "exp": 1234571490,
  "iat": 1234567890
}
```

**Validates:**
- ✅ Test user has `USER` role
- ✅ Test user has `CUSTOMER` role
- ✅ Email matches expected value
- ✅ Issuer is correct Keycloak realm

---

### **7. Invalid Credentials Rejection**
```java
@Test
@DisplayName("Invalid credentials are rejected")
void invalidCredentialsAreRejected()
```

**Verifies:**
- ✅ Wrong password is rejected
- ✅ Returns 401 Unauthorized
- ✅ Error type is `invalid_grant`

**Request:**
```http
POST /realms/openwallet/protocol/openid-connect/token

username=testuser
&password=wrongpassword
```

**Expected Response:**
```json
{
  "error": "invalid_grant",
  "error_description": "Invalid user credentials"
}
```

---

### **8. Client Configuration**
```java
@Test
@DisplayName("Client 'auth-service' is configured")
void authServiceClientExists()
```

**Verifies:**
- ✅ Client `auth-service` is registered
- ✅ Client secret is correct
- ✅ Service account is enabled
- ✅ Client credentials grant works

**Request (Service-to-Service Auth):**
```http
POST /realms/openwallet/protocol/openid-connect/token

grant_type=client_credentials
&client_id=auth-service
&client_secret=8hngZZARbVBbrnwgF6KG5KWbsPvWfAez
```

**Expected Result:** Access token for service account

---

### **9. Token Introspection**
```java
@Test
@DisplayName("Token introspection works")
void tokenIntrospectionWorks()
```

**Verifies:**
- ✅ Token introspection endpoint works
- ✅ Valid tokens are marked as `active: true`
- ✅ Token metadata is returned (username, roles, etc.)

**Request:**
```http
POST /realms/openwallet/protocol/openid-connect/token/introspect

token=<access-token>
&client_id=auth-service
&client_secret=8hngZZARbVBbrnwgF6KG5KWbsPvWfAez
```

**Expected Response:**
```json
{
  "active": true,
  "username": "testuser",
  "email": "testuser@openwallet.com",
  "scope": "profile email",
  ...
}
```

---

### **10. Configuration Summary**
```java
@Test
@DisplayName("Summary: Keycloak is fully configured")
void keycloakConfigurationSummary()
```

**Prints comprehensive summary:**
- ✅ Server URL
- ✅ Realm name
- ✅ Configured users
- ✅ Configured clients
- ✅ Token settings
- ✅ All test results

---

## 🚀 Running the Tests

### **Run all Keycloak tests:**
```bash
mvn test -Dtest=KeycloakConfigurationTest -pl integration-test
```

### **Run specific test:**
```bash
mvn test -Dtest=KeycloakConfigurationTest#testUserCanAuthenticate -pl integration-test
```

### **Run with verbose output:**
```bash
mvn test -Dtest=KeycloakConfigurationTest -pl integration-test -X
```

---

## 📊 Expected Output

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running KeycloakConfigurationTest
[INFO] Testing Keycloak at: http://localhost:54321
[INFO] Realm: openwallet

[INFO] ✓ Keycloak server is accessible at: http://localhost:54321
[INFO] ✓ Realm 'openwallet' exists and is properly configured
      - Public key available for JWT validation
[INFO] ✓ OpenID Connect configuration is available
      - Issuer: http://localhost:54321/realms/openwallet
      - Token Endpoint: http://localhost:54321/realms/openwallet/protocol/openid-connect/token
      - JWKS URI: http://localhost:54321/realms/openwallet/protocol/openid-connect/certs
[INFO] ✓ Test user 'testuser' authenticated successfully
      - Access token received
      - Refresh token received
      - Token expires in: 3600 seconds
[INFO] ✓ Admin user 'admin' authenticated successfully
[INFO] ✓ JWT token contains correct claims
      - Subject (user ID): a1b2c3d4-e5f6-7890-abcd-ef1234567890
      - Username: testuser
      - Email: testuser@openwallet.com
      - Roles: USER, CUSTOMER
      - Issuer: http://localhost:54321/realms/openwallet
[INFO] ✓ Invalid credentials properly rejected
      - Returned 401 Unauthorized
      - Error: invalid_grant
[INFO] ✓ Client 'auth-service' is properly configured
      - Client credentials grant works
      - Service account enabled
[INFO] ✓ Token introspection works
      - Token is active
      - Username: testuser

[INFO] ========================================
[INFO] KEYCLOAK CONFIGURATION SUMMARY
[INFO] ========================================
[INFO] ✓ Keycloak server: http://localhost:54321
[INFO] ✓ Realm: openwallet
[INFO] ✓ OpenID Connect configuration available
[INFO] ✓ Test users:
[INFO]     - admin/admin (ADMIN, USER roles)
[INFO]     - testuser/testpass (USER, CUSTOMER roles)
[INFO] ✓ Clients:
[INFO]     - auth-service (service account enabled)
[INFO]     - customer-service (bearer-only)
[INFO]     - wallet-service (bearer-only)
[INFO]     - frontend-app (public client)
[INFO] ✓ JWT tokens:
[INFO]     - Token lifespan: 3600 seconds (1 hour)
[INFO]     - Contains user claims (sub, email, roles)
[INFO]     - Signed with RS256
[INFO] ✓ Token operations:
[INFO]     - Authentication: PASS
[INFO]     - Authorization: PASS
[INFO]     - Introspection: PASS
[INFO]     - Invalid credentials rejection: PASS
[INFO] ========================================
[INFO] Keycloak is ready for integration tests!
[INFO] ========================================

[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

---

## 🎯 What This Validates

| Component | Validation |
|-----------|------------|
| **Realm** | ✅ Exists and accessible |
| **Clients** | ✅ Registered with correct secrets |
| **Users** | ✅ Can authenticate |
| **JWT Tokens** | ✅ Issued with correct claims |
| **Roles** | ✅ Properly assigned to users |
| **OIDC** | ✅ Discovery endpoint works |
| **Security** | ✅ Invalid credentials rejected |
| **Service Accounts** | ✅ Client credentials flow works |
| **Introspection** | ✅ Token validation works |

---

## 🔧 Troubleshooting

### **Test Fails: "Keycloak server is not accessible"**
- **Cause**: Keycloak container not started
- **Fix**: Ensure `InfrastructureManager.start()` runs before tests
- **Check**: `docker ps | grep keycloak`

### **Test Fails: "Realm 'openwallet' does not exist"**
- **Cause**: Realm import failed
- **Fix**: Check `keycloak-realm.json` exists in `src/test/resources/`
- **Check**: Keycloak logs for import errors

### **Test Fails: "Test user cannot authenticate"**
- **Cause**: User not created or wrong credentials
- **Fix**: Verify `keycloak-realm.json` contains test users
- **Check**: Keycloak Admin Console → Users

### **Test Fails: "JWT token missing claims"**
- **Cause**: Client scopes not configured
- **Fix**: Verify `keycloak-realm.json` includes protocol mappers
- **Check**: Keycloak Admin Console → Client Scopes

---

## 📝 Integration with Other Tests

These tests should run **BEFORE** service tests to ensure Keycloak is ready:

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTestSuite {
    
    @Test
    @Order(1)
    void keycloakConfiguration() {
        // Run KeycloakConfigurationTest
    }
    
    @Test
    @Order(2)
    void authServiceTests() {
        // Run AuthServiceIntegrationTest
    }
    
    @Test
    @Order(3)
    void userOnboardingFlow() {
        // Run UserOnboardingFlowTest
    }
}
```

---

## 🎉 Summary

**KeycloakConfigurationTest** provides comprehensive validation that:

1. ✅ Keycloak is running
2. ✅ Realm is configured
3. ✅ Clients are registered
4. ✅ Users can authenticate
5. ✅ JWT tokens are correct
6. ✅ Security works properly

**Result**: Full confidence that Keycloak is ready for integration tests! 🚀

