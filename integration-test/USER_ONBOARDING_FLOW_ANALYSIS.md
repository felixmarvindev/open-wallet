# User Onboarding Flow - Complete Analysis

## 📋 Overview

The user onboarding flow is a critical end-to-end journey that spans multiple services and infrastructure components. This document analyzes every interaction point.

---

## 🎯 Flow Stages

```
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Frontend  │───▶│ Auth Service │───▶│   Customer   │───▶│  Complete!   │
│    (User)   │    │  (Register)  │    │   Service    │    │   Onboarded  │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
     │                    │                     │
     │                    ▼                     ▼
     │              ┌──────────┐         ┌──────────┐
     │              │  Kafka   │         │   JWT    │
     │              │  Events  │         │   Auth   │
     │              └──────────┘         └──────────┘
     │                    │                     │
     ▼                    ▼                     ▼
┌──────────┐       ┌──────────┐         ┌──────────┐
│   HTTP   │       │PostgreSQL│         │ Keycloak │
│   API    │       │Database  │         │   IAM    │
└──────────┘       └──────────┘         └──────────┘
```

---

## 🔍 Detailed Flow Analysis

### **Stage 1: User Registration**

#### **Frontend (Client Side)**
```javascript
// What happens in the UI
1. User fills registration form:
   - Username
   - Email
   - Password
   
2. Frontend validates input:
   - Email format
   - Password strength
   - Required fields
   
3. Frontend makes HTTP POST request:
   POST /api/v1/auth/register
   {
     "username": "john_doe",
     "email": "john@example.com",
     "password": "SecurePassword123!"
   }
   
4. Frontend receives response:
   201 Created
   {
     "userId": "uuid-here",
     "username": "john_doe",
     "email": "john@example.com",
     "createdAt": "2024-01-01T10:00:00Z"
   }
   
5. Frontend shows success message
6. Frontend redirects to login or auto-login
```

#### **Auth Service (Backend)**
```
HTTP Request → Controller → Service Layer → Repository → Database
                    ↓
                 Kafka Producer
```

**Detailed Steps:**

1. **Controller Layer** (`AuthController`)
   ```java
   @PostMapping("/register")
   public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request)
   ```
   - Receives HTTP request
   - Validates request body
   - Delegates to service

2. **Service Layer** (`AuthService`)
   - Validates business rules:
     * Username uniqueness
     * Email uniqueness
     * Password requirements
   - Hashes password (BCrypt)
   - Creates User entity
   - Saves to database

3. **Repository Layer** (`UserRepository`)
   - Executes SQL INSERT
   - Returns saved entity with ID

4. **Event Publishing**
   - Creates `USER_REGISTERED` event
   - Publishes to Kafka topic: `user-events`
   ```json
   {
     "eventType": "USER_REGISTERED",
     "userId": "uuid",
     "username": "john_doe",
     "email": "john@example.com",
     "timestamp": "2024-01-01T10:00:00Z"
   }
   ```

5. **Response**
   - Returns 201 Created
   - User DTO in response body

#### **Service-to-Infrastructure Interactions**

| Component | Interaction | Purpose |
|-----------|-------------|---------|
| **PostgreSQL** | `INSERT INTO users (id, username, email, password_hash, ...)` | Persist user data |
| **Kafka** | `producer.send("user-events", event)` | Publish domain event |
| **Database Connection Pool** | Get/Release connection | Connection management |

---

### **Stage 2: Event Propagation (Async)**

#### **Kafka Message Broker**
```
Auth Service ──[publish]──▶ Kafka ──[subscribe]──▶ Other Services
                            (Topic: user-events)
```

**What Happens:**
1. Auth service publishes `USER_REGISTERED` event
2. Kafka stores event in `user-events` topic
3. Multiple services can consume this event:
   - Customer Service (listens for user creation)
   - Notification Service (sends welcome email)
   - Analytics Service (tracks registration metrics)
   - Audit Service (logs user activity)

**Event Schema:**
```json
{
  "eventType": "USER_REGISTERED",
  "eventId": "event-uuid",
  "userId": "user-uuid",
  "username": "john_doe",
  "email": "john@example.com",
  "timestamp": "2024-01-01T10:00:00Z",
  "metadata": {
    "source": "auth-service",
    "version": "1.0"
  }
}
```

---

### **Stage 3: User Login**

#### **Frontend (Client Side)**
```javascript
1. User enters credentials:
   - Username
   - Password
   
2. Frontend makes HTTP POST request:
   POST /api/v1/auth/login
   {
     "username": "john_doe",
     "password": "SecurePassword123!"
   }
   
3. Frontend receives JWT token:
   200 OK
   {
     "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     "refreshToken": "refresh-token-here",
     "expiresIn": 3600,
     "tokenType": "Bearer"
   }
   
4. Frontend stores token:
   - localStorage.setItem('accessToken', token)
   - OR sessionStorage
   - OR secure HttpOnly cookie
   
5. Frontend redirects to dashboard
```

#### **Auth Service (Backend)**

**Detailed Steps:**

1. **Authentication Process**
   ```
   Request → Controller → AuthService
                            ↓
                    Verify Credentials
                            ↓
                    Query Database
                            ↓
                    Compare Password Hash
                            ↓
                    Generate JWT Token
                            ↓
                    Return Token
   ```

2. **Service Layer Logic**
   - Fetch user by username
   - Verify password (BCrypt compare)
   - If valid:
     * Generate JWT token
     * Include user claims (userId, username, roles)
     * Set expiration time
     * Sign with secret key
   - If invalid:
     * Return 401 Unauthorized

3. **JWT Token Structure**
   ```json
   {
     "header": {
       "alg": "HS256",
       "typ": "JWT"
     },
     "payload": {
       "sub": "user-uuid",
       "username": "john_doe",
       "email": "john@example.com",
       "roles": ["USER"],
       "iat": 1234567890,
       "exp": 1234571490
     },
     "signature": "..."
   }
   ```

#### **Service-to-Infrastructure Interactions**

| Component | Interaction | Purpose |
|-----------|-------------|---------|
| **PostgreSQL** | `SELECT * FROM users WHERE username = ?` | Fetch user for auth |
| **Keycloak** | JWT issuer/validator | Token generation & validation |
| **Redis** (Optional) | Store refresh token | Session management |

---

### **Stage 4: Create Customer Profile**

#### **Frontend (Client Side)**
```javascript
1. User fills customer profile form:
   - First Name
   - Last Name
   - Phone Number
   - Date of Birth
   - Address
   
2. Frontend includes JWT in request:
   POST /api/v1/customers
   Headers: {
     "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   }
   Body: {
     "userId": "user-uuid",
     "firstName": "John",
     "lastName": "Doe",
     "email": "john@example.com",
     "phoneNumber": "+254712345678",
     "dateOfBirth": "1990-01-01",
     "address": "123 Test Street, Nairobi"
   }
   
3. Frontend receives response:
   201 Created
   {
     "id": "customer-uuid",
     "userId": "user-uuid",
     "firstName": "John",
     "lastName": "Doe",
     "email": "john@example.com",
     "phoneNumber": "+254712345678",
     "kycStatus": "PENDING",
     "createdAt": "2024-01-01T10:05:00Z"
   }
   
4. Frontend shows success message
5. Frontend redirects to dashboard/KYC flow
```

#### **Customer Service (Backend)**

**Detailed Steps:**

1. **Security Filter Chain**
   ```
   HTTP Request → JWT Filter → Validate Token → Extract User Info → Continue
   ```
   - Extract JWT from Authorization header
   - Validate token signature (using Keycloak public key)
   - Validate expiration
   - Extract user claims (userId, roles)
   - Store in SecurityContext

2. **Controller Layer** (`CustomerController`)
   ```java
   @PostMapping("/customers")
   @PreAuthorize("hasRole('USER')")
   public ResponseEntity<CustomerResponse> createCustomer(
       @RequestBody CreateCustomerRequest request,
       @AuthenticationPrincipal UserPrincipal user
   )
   ```
   - Receives authenticated request
   - Validates request body
   - Delegates to service

3. **Service Layer** (`CustomerService`)
   - Validates business rules:
     * User exists
     * Customer doesn't already exist for user
     * Phone number format
     * Email matches user email
   - Creates Customer entity
   - Links to userId
   - Sets initial KYC status: PENDING
   - Saves to database

4. **Repository Layer** (`CustomerRepository`)
   - Executes SQL INSERT
   - Returns saved entity with ID

5. **Response**
   - Returns 201 Created
   - Customer DTO in response body

#### **Service-to-Service Communication**

**Customer Service → Auth Service** (Optional)
```
GET /api/v1/users/{userId}
Headers: {
  "Authorization": "Bearer service-to-service-token"
}

Purpose: Verify user exists and get additional details
Response: User information
```

#### **Service-to-Infrastructure Interactions**

| Component | Interaction | Purpose |
|-----------|-------------|---------|
| **PostgreSQL** | `INSERT INTO customers (...)` | Persist customer data |
| **Keycloak** | JWT validation | Verify access token |
| **Auth Service** (Optional) | HTTP GET /users/{userId} | Verify user exists |

---

## 🏗️ Infrastructure Requirements

### **Required Services**

#### **1. Auth Service** (Port 9001)
- **Purpose**: User authentication & authorization
- **Dependencies**:
  - PostgreSQL (user data)
  - Kafka (event publishing)
  - Keycloak (JWT issuer)
- **Endpoints**:
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /actuator/health`

#### **2. Customer Service** (Port 9002)
- **Purpose**: Customer profile management
- **Dependencies**:
  - PostgreSQL (customer data)
  - Keycloak (JWT validation)
  - Auth Service (optional, for user verification)
- **Endpoints**:
  - `POST /api/v1/customers`
  - `GET /api/v1/customers/{id}`
  - `GET /actuator/health`

### **Required Infrastructure**

#### **1. PostgreSQL** (TestContainers)
- **Purpose**: Primary data store
- **Databases**:
  - `openwallet_test` (shared)
- **Tables**:
  - `users` (auth-service)
  - `customers` (customer-service)
  - `customer_user_mapping` (customer-service)
  - `kyc_checks` (customer-service)

#### **2. Apache Kafka** (TestContainers)
- **Purpose**: Event streaming & async communication
- **Topics**:
  - `user-events` (USER_REGISTERED, USER_UPDATED, etc.)
  - `customer-events` (CUSTOMER_CREATED, KYC_UPDATED, etc.)
- **Consumer Groups**:
  - `auth-service-group`
  - `customer-service-group`

#### **3. Keycloak** (TestContainers)
- **Purpose**: Identity & Access Management
- **Configuration**:
  - Realm: `openwallet`
  - Client: `auth-service`
  - JWT issuer & validator
  - Public key for token verification

---

## 🔄 Complete Sequence Diagram

```
Frontend          Auth Service      PostgreSQL      Kafka         Customer Service    Keycloak
   │                   │                │            │                  │               │
   │                   │                │            │                  │               │
   │─────Register────▶ │                │            │                  │               │
   │  POST /register   │                │            │                  │               │
   │                   │                │            │                  │               │
   │                   │──Save User────▶│            │                  │               │
   │                   │   INSERT       │            │                  │               │
   │                   │◀──User Saved───│            │                  │               │
   │                   │    (userId)    │            │                  │               │
   │                   │                │            │                  │               │
   │                   │──Publish──────────────────▶│                  │               │
   │                   │  USER_REGISTERED           │                  │               │
   │                   │                │            │                  │               │
   │◀──201 Created─────│                │            │                  │               │
   │  (userId)         │                │            │                  │               │
   │                   │                │            │                  │               │
   │─────Login────────▶│                │            │                  │               │
   │  POST /login      │                │            │                  │               │
   │                   │                │            │                  │               │
   │                   │──Get User─────▶│            │                  │               │
   │                   │   SELECT       │            │                  │               │
   │                   │◀──User Data────│            │                  │               │
   │                   │                │            │                  │               │
   │                   │──Generate JWT──────────────────────────────────────────────▶  │
   │                   │                │            │                  │               │
   │                   │◀──JWT Token────────────────────────────────────────────────── │
   │                   │                │            │                  │               │
   │◀──200 OK──────────│                │            │                  │               │
   │  (accessToken)    │                │            │                  │               │
   │                   │                │            │                  │               │
   │──Create Customer───────────────────────────────────────────────▶  │               │
   │  POST /customers  │                │            │                  │               │
   │  + JWT Token      │                │            │                  │               │
   │                   │                │            │                  │               │
   │                   │                │            │                  │──Validate JWT──────▶│
   │                   │                │            │                  │               │
   │                   │                │            │                  │◀──JWT Valid────────│
   │                   │                │            │                  │               │
   │                   │                │            │                  │──Save────▶   │
   │                   │                │            │                  │  INSERT  │   │
   │                   │                │            │                  │◀─Saved───│   │
   │                   │                │            │                  │ (custId) │   │
   │                   │                │            │                  │          │   │
   │◀──201 Created─────────────────────────────────────────────────────│          │   │
   │  (customerId)     │                │            │                  │          │   │
   │                   │                │            │                  │          │   │
```

---

## 📊 Data Flow

### **1. User Registration Data**
```
Frontend ──▶ Auth Service ──▶ PostgreSQL (users table)
                  │
                  └────────▶ Kafka (user-events topic)
```

### **2. Authentication Data**
```
Frontend ──▶ Auth Service ──▶ PostgreSQL (query)
                  │
                  └────────▶ Keycloak (JWT generation)
                  │
                  └────────▶ Frontend (JWT token)
```

### **3. Customer Profile Data**
```
Frontend ──▶ Customer Service ──▶ Keycloak (JWT validation)
   (+ JWT)          │
                    └────────▶ PostgreSQL (customers table)
                    │
                    └────────▶ Frontend (customer profile)
```

---

## ✅ Services Required for Test

### **Minimum Required Services**

1. ✅ **Auth Service** - User registration & login
2. ✅ **Customer Service** - Customer profile creation

### **Required Infrastructure**

3. ✅ **PostgreSQL** - Data persistence
4. ✅ **Kafka** - Event streaming
5. ✅ **Keycloak** - JWT auth

### **Optional (For Future)**

6. ⏳ **Notification Service** - Welcome emails
7. ⏳ **Analytics Service** - User metrics
8. ⏳ **Audit Service** - Activity logging

---

## 🧪 Test Coverage

The `UserOnboardingFlowTest` validates:

| Step | What's Tested | Service | Infrastructure |
|------|---------------|---------|----------------|
| 1 | User registration | Auth Service | PostgreSQL |
| 2 | Event publishing | Auth Service | Kafka |
| 3 | Event consumption | Test verifier | Kafka |
| 4 | User login | Auth Service | PostgreSQL, Keycloak |
| 5 | JWT generation | Auth Service | Keycloak |
| 6 | JWT validation | Customer Service | Keycloak |
| 7 | Customer creation | Customer Service | PostgreSQL |
| 8 | User-Customer linking | Customer Service | PostgreSQL |

---

## 🚀 Startup Sequence

For the test to pass, services must start in this order:

```
1. Infrastructure (parallel):
   ├── PostgreSQL (TestContainers)
   ├── Kafka (TestContainers)
   └── Keycloak (TestContainers)
   
2. Microservices (parallel - after infrastructure):
   ├── Auth Service (depends on PostgreSQL, Kafka, Keycloak)
   └── Customer Service (depends on PostgreSQL, Keycloak)
```

**Our `ServiceContainerManager` handles this automatically!** ✅

---

## 📝 Summary

### **Services Needed**
- ✅ Auth Service (Port 9001)
- ✅ Customer Service (Port 9002)

### **Infrastructure Needed**
- ✅ PostgreSQL (dynamic port via TestContainers)
- ✅ Kafka (dynamic port via TestContainers)
- ✅ Keycloak (dynamic port via TestContainers)

### **Key Interactions**
- Frontend ↔ Auth Service (HTTP/REST)
- Frontend ↔ Customer Service (HTTP/REST + JWT)
- Auth Service ↔ PostgreSQL (JDBC)
- Customer Service ↔ PostgreSQL (JDBC)
- Auth Service → Kafka (Producer)
- Auth Service ↔ Keycloak (JWT)
- Customer Service ↔ Keycloak (JWT validation)

### **Data Flow**
1. User data persisted in PostgreSQL
2. Events published to Kafka
3. JWT tokens managed by Keycloak
4. Customer data linked to user via userId

---

**Ready to run the test!** 🎉

All services and infrastructure are configured correctly via `ServiceContainerManager.startAll()`.

