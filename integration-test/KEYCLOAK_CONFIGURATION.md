# Keycloak Configuration for Integration Tests

## ✅ Automatic Setup

Keycloak is **automatically configured** when the infrastructure starts! No manual setup required.

---

## 🔧 How It Works

### **1. Realm Import on Startup**

The `InfrastructureManager` automatically imports the realm configuration:

```java
keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
    .withRealmImportFile("keycloak-realm.json")  // ← Imports realm automatically!
    .withAdminUsername("admin")
    .withAdminPassword("admin")
    // ... other config
```

### **2. Configuration File**

Location: `integration-test/src/test/resources/keycloak-realm.json`

This JSON file contains:
- ✅ Realm: `openwallet`
- ✅ Clients (services)
- ✅ Roles
- ✅ Test users
- ✅ Token settings
- ✅ Security policies

---

## 📦 What Gets Configured

### **Realm: `openwallet`**

```json
{
  "realm": "openwallet",
  "enabled": true,
  "accessTokenLifespan": 3600,
  "sslRequired": "none"
}
```

| Setting | Value | Purpose |
|---------|-------|---------|
| Realm Name | `openwallet` | Namespace for all auth |
| Access Token Lifespan | 3600s (1 hour) | JWT validity period |
| SSL Required | None | Allow HTTP for tests |
| Registration Allowed | True | Users can register |

---

### **Clients (Services)**

#### **1. auth-service**
```json
{
  "clientId": "auth-service",
  "secret": "8hngZZARbVBbrnwgF6KG5KWbsPvWfAez",
  "serviceAccountsEnabled": true,
  "directAccessGrantsEnabled": true
}
```

**Purpose**: Authentication & authorization service  
**Features**:
- ✅ Generate JWT tokens
- ✅ Validate user credentials
- ✅ Service-to-service auth

#### **2. customer-service**
```json
{
  "clientId": "customer-service",
  "secret": "customer-service-secret-key",
  "bearerOnly": true
}
```

**Purpose**: Customer profile management  
**Features**:
- ✅ Validate JWT tokens
- ✅ Bearer-only (doesn't issue tokens)

#### **3. wallet-service**
```json
{
  "clientId": "wallet-service",
  "secret": "wallet-service-secret-key",
  "bearerOnly": true
}
```

**Purpose**: Digital wallet operations  
**Features**:
- ✅ Validate JWT tokens
- ✅ Protected resource server

#### **4. frontend-app**
```json
{
  "clientId": "frontend-app",
  "publicClient": true,
  "redirectUris": ["http://localhost:3000/*"]
}
```

**Purpose**: Web/mobile frontend  
**Features**:
- ✅ Public client (no secret)
- ✅ PKCE flow support
- ✅ CORS configured

---

### **Roles**

| Role | Description | Used By |
|------|-------------|---------|
| `USER` | Standard user | All registered users |
| `ADMIN` | Administrator | System admins |
| `CUSTOMER` | Customer with wallet | Users with customer profile |

---

### **Test Users**

#### **Admin User**
```json
{
  "username": "admin",
  "password": "admin",
  "email": "admin@openwallet.com",
  "roles": ["ADMIN", "USER"]
}
```

**Use for**: Administrative operations, system management

#### **Test User**
```json
{
  "username": "testuser",
  "password": "testpass",
  "email": "testuser@openwallet.com",
  "roles": ["USER", "CUSTOMER"]
}
```

**Use for**: Standard user flow testing

---

## 🔐 JWT Token Configuration

### **Token Contents**

When a user logs in, the JWT includes:

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-id"
  },
  "payload": {
    "sub": "user-uuid",
    "preferred_username": "john_doe",
    "email": "john@example.com",
    "email_verified": true,
    "realm_access": {
      "roles": ["USER", "CUSTOMER"]
    },
    "iat": 1234567890,
    "exp": 1234571490,
    "iss": "http://localhost:8080/realms/openwallet"
  }
}
```

### **Token Lifespans**

| Token Type | Lifespan | Purpose |
|------------|----------|---------|
| Access Token | 1 hour (3600s) | API access |
| Refresh Token | 30 days | Get new access tokens |
| SSO Session | 10 hours | Single sign-on |
| Offline Token | 30 days | Offline access |

---

## 🚀 Usage in Tests

### **Getting JWT Token**

```java
// Option 1: Login with test user
POST /api/v1/auth/login
{
  "username": "testuser",
  "password": "testpass"
}

Response:
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "expiresIn": 3600
}

// Option 2: Use pre-configured admin
POST /api/v1/auth/login
{
  "username": "admin",
  "password": "admin"
}
```

### **Validating JWT Token**

Services automatically validate JWT tokens using Keycloak:

```java
// In Customer Service (automatic via Spring Security)
@GetMapping("/customers/{id}")
@PreAuthorize("hasRole('USER')")
public CustomerDTO getCustomer(
    @PathVariable String id,
    @AuthenticationPrincipal UserPrincipal user  // Auto-extracted from JWT
) {
    // user.getId() → from JWT "sub" claim
    // user.getUsername() → from JWT "preferred_username" claim
    // user.getRoles() → from JWT "realm_access.roles" claim
}
```

---

## 🔍 Verification

### **Check Realm Exists**

```bash
# After infrastructure starts
curl http://localhost:<keycloak-port>/realms/openwallet

Response:
{
  "realm": "openwallet",
  "public_key": "...",
  "token-service": "http://localhost:8080/realms/openwallet/protocol/openid-connect",
  ...
}
```

### **Check Client Exists**

```bash
# Login to Keycloak Admin Console
http://localhost:<keycloak-port>/admin
Username: admin
Password: admin

# Navigate to:
Realm: openwallet → Clients
```

You should see:
- ✅ auth-service
- ✅ customer-service
- ✅ wallet-service
- ✅ frontend-app

---

## 🎯 Integration Test Flow

```
1. InfrastructureManager.start()
   ├── Start PostgreSQL
   ├── Start Kafka
   └── Start Keycloak
       └── Import keycloak-realm.json  ← Automatic!
       
2. Keycloak is ready with:
   ├── Realm: openwallet
   ├── Clients: auth-service, customer-service, etc.
   ├── Roles: USER, ADMIN, CUSTOMER
   └── Test users: admin, testuser
   
3. Services start:
   ├── Auth Service
   │   └── Issues JWT tokens via Keycloak
   └── Customer Service
       └── Validates JWT tokens via Keycloak
       
4. Tests run:
   ├── Login → Get JWT
   ├── Use JWT in API calls
   └── Services validate JWT automatically
```

---

## 📋 Summary

### **✅ What's Automatic**

1. Keycloak container starts
2. Realm `openwallet` is created
3. All clients are registered
4. Roles are configured
5. Test users are created
6. JWT configuration is set

### **❌ What You DON'T Need to Do**

- ❌ Manually create realm
- ❌ Manually register clients
- ❌ Manually configure roles
- ❌ Manually create test users
- ❌ Manually set token lifespans

### **✅ What You CAN Do**

- ✅ Use test users immediately
- ✅ Generate JWT tokens
- ✅ Validate JWT tokens
- ✅ Test role-based access control
- ✅ Test token expiration

---

## 🔧 Customizing Configuration

To modify Keycloak settings:

1. **Edit the realm file**: `integration-test/src/test/resources/keycloak-realm.json`
2. **Change token lifespans**: Update `accessTokenLifespan`
3. **Add new clients**: Add to `clients` array
4. **Add new roles**: Add to `roles.realm` array
5. **Add test users**: Add to `users` array

**No code changes needed!** The realm file is automatically imported on next test run.

---

## 🎉 Conclusion

**Keycloak is fully configured automatically!**

Just start your tests:
```bash
mvn test -Dtest=UserOnboardingFlowTest -pl integration-test
```

Keycloak will:
- ✅ Start with PostgreSQL backend
- ✅ Import the `openwallet` realm
- ✅ Configure all clients and users
- ✅ Be ready for JWT operations

**Zero manual configuration required!** 🚀

