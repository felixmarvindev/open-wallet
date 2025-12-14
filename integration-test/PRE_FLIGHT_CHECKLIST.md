# Pre-Flight Checklist for User Onboarding Test

## ✅ Infrastructure Status

| Component | Status | Test File |
|-----------|--------|-----------|
| PostgreSQL | ✅ Verified | `InfrastructureTest` |
| Kafka | ✅ Verified | `InfrastructureTest` |
| Keycloak | ✅ Verified | `KeycloakConfigurationTest` |
| Realm Config | ✅ Verified | `KeycloakConfigurationTest` |
| JWT Tokens | ✅ Verified | `KeycloakConfigurationTest` |

---

## ✅ Service Status

| Service | Status | Test File |
|---------|--------|-----------|
| Auth Service | ✅ Starts | `ServiceStartupProofTest` |
| Customer Service | ✅ Starts | `ServiceStartupProofTest` |
| Service Manager | ✅ Works | `ServiceContainerManager` |

---

## ⏳ Endpoint Requirements

For `UserOnboardingFlowTest` to pass, the following endpoints must be implemented:

### **Auth Service** (Port 9001)

#### 1. User Registration
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePassword123!"
}

Expected Response: 201 Created
{
  "userId": "uuid",
  "username": "john_doe",
  "email": "john@example.com",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

**Requirements:**
- ✅ Validates input (email format, password strength)
- ✅ Creates user in PostgreSQL
- ✅ Publishes `USER_REGISTERED` event to Kafka topic `user-events`
- ✅ Returns 201 with user details

#### 2. User Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "SecurePassword123!"
}

Expected Response: 200 OK
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Requirements:**
- ✅ Validates credentials against PostgreSQL
- ✅ Generates JWT token via Keycloak
- ✅ Returns 200 with tokens

### **Customer Service** (Port 9002)

#### 3. Create Customer Profile
```http
POST /api/v1/customers
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
  "userId": "uuid",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phoneNumber": "+254712345678",
  "dateOfBirth": "1990-01-01",
  "address": "123 Test Street, Nairobi"
}

Expected Response: 201 Created
{
  "id": "customer-uuid",
  "userId": "uuid",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phoneNumber": "+254712345678",
  "kycStatus": "PENDING",
  "createdAt": "2024-01-01T10:05:00Z"
}
```

**Requirements:**
- ✅ Validates JWT token via Keycloak
- ✅ Extracts user info from JWT
- ✅ Creates customer in PostgreSQL
- ✅ Links customer to userId
- ✅ Returns 201 with customer details

---

## 🔍 Quick Verification

Before running the full onboarding test, let's verify the endpoints exist:

### **Check Auth Service Endpoints**

Run this test first:
```bash
mvn test -Dtest=AuthServiceIntegrationTest -pl integration-test
```

This verifies:
- ✅ Auth service starts
- ✅ Health endpoint works
- ✅ Register endpoint exists

### **Check Service Startup**

Already verified by:
```bash
mvn test -Dtest=ServiceStartupProofTest -pl integration-test
```

---

## 🚀 Ready for User Onboarding Test?

### **Prerequisites Checklist**

- [x] PostgreSQL running in TestContainers
- [x] Kafka running in TestContainers
- [x] Keycloak running in TestContainers
- [x] Keycloak realm `openwallet` configured
- [x] Keycloak test users exist
- [x] Keycloak clients registered
- [x] JWT tokens working
- [x] Auth service can start
- [x] Customer service can start
- [ ] Auth service `/register` endpoint implemented
- [ ] Auth service `/login` endpoint implemented
- [ ] Customer service `/customers` POST endpoint implemented
- [ ] Kafka event publishing implemented
- [ ] JWT validation in customer service implemented

---

## ⚠️ Potential Issues

### **If UserOnboardingFlowTest Fails:**

#### **Scenario 1: Registration Endpoint Not Found (404)**
```
Expected: 201 Created
Actual: 404 Not Found
```

**Cause**: `/api/v1/auth/register` endpoint not implemented  
**Fix**: Implement registration endpoint in auth-service

#### **Scenario 2: Login Returns 500**
```
Expected: 200 OK
Actual: 500 Internal Server Error
```

**Cause**: Keycloak integration not working or credentials validation failing  
**Fix**: Check auth-service Keycloak configuration and database queries

#### **Scenario 3: Customer Creation Fails (401)**
```
Expected: 201 Created
Actual: 401 Unauthorized
```

**Cause**: JWT validation not working in customer-service  
**Fix**: Check Spring Security OAuth2 configuration in customer-service

#### **Scenario 4: No Kafka Event Published**
```
Expected: USER_REGISTERED event
Actual: Timeout waiting for event
```

**Cause**: Kafka producer not configured or not publishing  
**Fix**: Check Kafka configuration in auth-service

---

## 🎯 Next Steps

### **Option 1: Run Full Onboarding Test (Optimistic)**

If you believe all endpoints are implemented:

```bash
mvn test -Dtest=UserOnboardingFlowTest -pl integration-test
```

### **Option 2: Verify Endpoints First (Safer)**

Check if endpoints exist before full test:

```bash
# Start services manually and test endpoints
curl -X POST http://localhost:9001/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"pass123"}'

curl -X POST http://localhost:9001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"pass123"}'
```

---

## 📊 What to Expect

### **If All Endpoints Are Implemented:**

```
[INFO] Running UserOnboardingFlowTest
[INFO] Starting services for user onboarding flow test...
[INFO] ✓ All services started and ready for testing

[INFO] Step 1: Registering new user...
[INFO] User registered successfully
[INFO] Registered user - ID: uuid, Username: testuser_123, Email: testuser@example.com

[INFO] Step 2: Verifying USER_REGISTERED event...
[INFO] ✓ USER_REGISTERED event verified

[INFO] Step 3: Logging in...
[INFO] ✓ Login successful, received access token

[INFO] Step 4: Creating customer profile...
[INFO] ✓ Customer profile created successfully

[INFO] ✓ Customer verification passed - ID: customer-uuid
[INFO] ✓ Complete user onboarding flow successful!

Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 ✅
```

### **If Endpoints Are NOT Implemented:**

```
[INFO] Step 1: Registering new user...
[ERROR] Expected: 201 Created
[ERROR] Actual: 404 Not Found

AssertionFailedError: Health endpoint should return 200
```

---

## 🎯 **Recommendation**

Since you have:
- ✅ Keycloak fully configured and tested
- ✅ Infrastructure working
- ✅ Services starting successfully

**You're ready to test!** 🚀

Run:
```bash
mvn test -Dtest=UserOnboardingFlowTest -pl integration-test
```

If endpoints are missing, the test will tell you exactly which ones, and you can implement them one by one.

---

## 📝 **Summary**

**Infrastructure:** ✅ READY  
**Keycloak:** ✅ READY  
**Services:** ✅ READY  
**Endpoints:** ⏳ TO BE VERIFIED

**Next Command:**
```bash
mvn test -Dtest=UserOnboardingFlowTest -pl integration-test
```

Let's find out what needs to be implemented! 🎉

