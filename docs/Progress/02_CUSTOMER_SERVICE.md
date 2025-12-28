# Customer Service - Current State

## Overview

The Customer Service manages customer profiles, handles KYC (Know Your Customer) verification lifecycle, and automatically creates customer records when users register. It serves as the bridge between authentication and wallet services.

## Implemented Features

### ✅ Customer Profile Management

#### 1. Create Customer
- **Endpoint**: `POST /api/v1/customers`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Creates customer profile linked to userId
  - Validates required fields (firstName, lastName, email, phone)
  - Publishes `CUSTOMER_CREATED` event to Kafka
  - Returns customer ID and profile data
- **Auto-Creation**: Customer is automatically created when `USER_REGISTERED` event is consumed
- **Validation**:
  - firstName: required
  - lastName: required
  - email: required, valid format
  - phone: required, valid format
  - dateOfBirth: optional
  - address: optional
- **Integration Tests**: ✅ Covered in `CustomerProfileCrudTest`, `UserOnboardingFlowTest`

#### 2. Get My Profile
- **Endpoint**: `GET /api/v1/customers/me`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Retrieves current authenticated user's customer profile
  - Extracts userId from JWT token
  - Returns customer profile with all fields
- **Security**: Only returns profile for authenticated user
- **Integration Tests**: ✅ Covered in `CustomerProfileCrudTest`

#### 3. Update My Profile
- **Endpoint**: `PUT /api/v1/customers/me`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Updates customer profile fields
  - Validates updated data
  - Returns updated profile
- **Allowed Updates**: firstName, lastName, email, phone, dateOfBirth, address
- **Integration Tests**: ✅ Covered in `CustomerProfileCrudTest`

### ✅ KYC (Know Your Customer) Verification

#### 1. Initiate KYC
- **Endpoint**: `POST /api/v1/customers/me/kyc/initiate`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Creates KYC check record with status `IN_PROGRESS`
  - Validates no existing in-progress KYC
  - Generates provider reference ID
  - Accepts document information
  - Publishes `KYC_INITIATED` event
- **Request Fields**:
  - documents: List of document information
- **Integration Tests**: ✅ Covered in `KycVerificationFlowTest`

#### 2. Get KYC Status
- **Endpoint**: `GET /api/v1/customers/me/kyc/status`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Returns current KYC status
  - Returns most recent KYC check
  - Status values: `PENDING`, `IN_PROGRESS`, `VERIFIED`, `REJECTED`
- **Response Fields**:
  - status: Current KYC status
  - verifiedAt: Verification timestamp (if verified)
  - rejectionReason: Rejection reason (if rejected)
- **Integration Tests**: ✅ Covered in `KycVerificationFlowTest`

#### 3. KYC Webhook
- **Endpoint**: `POST /api/v1/customers/kyc/webhook`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Receives KYC verification results from external provider
  - Updates KYC status to `VERIFIED` or `REJECTED`
  - Publishes `KYC_VERIFIED` or `KYC_REJECTED` events
  - Handles idempotency (prevents duplicate processing)
- **Webhook Fields**:
  - customerId: Customer ID
  - status: "VERIFIED" or "REJECTED"
  - verifiedAt: Verification timestamp
  - rejectionReason: Reason for rejection (if rejected)
- **Integration Tests**: ✅ Covered in `KycVerificationFlowTest`

### ✅ Event-Driven Architecture

#### Event Consumption
- **Topic**: `user-events`
- **Event**: `USER_REGISTERED`
- **Action**: Automatically creates customer profile
- **Status**: ✅ Implemented and Verified
- **Listener**: `UserEventListener`

#### Event Publishing
- **Topic**: `customer-events`
- **Events Published**:
  - `CUSTOMER_CREATED`: When customer profile is created
  - `KYC_VERIFIED`: When KYC is verified
  - `KYC_REJECTED`: When KYC is rejected
  - `KYC_INITIATED`: When KYC process starts
- **Status**: ✅ Implemented and Verified

## Architecture

### Service Components

```
CustomerController
  ├── CustomerService
  │   ├── CustomerRepository
  │   └── CustomerEventProducer
  ├── KycService
  │   ├── KycCheckRepository
  │   └── KycEventProducer
  ├── UserEventListener (Kafka consumer)
  └── CustomerExceptionHandler
```

### Database Schema

#### Customers Table
- `id`: Primary key
- `user_id`: Foreign key to Keycloak user
- `first_name`, `last_name`: Name fields
- `email`, `phone`: Contact information
- `date_of_birth`: Optional
- `address`: JSON field for address data
- `created_at`, `updated_at`: Timestamps

#### KycChecks Table
- `id`: Primary key
- `customer_id`: Foreign key to customers
- `status`: Enum (PENDING, IN_PROGRESS, VERIFIED, REJECTED)
- `provider_reference`: External provider reference
- `documents`: JSON field for document information
- `verified_at`: Verification timestamp
- `rejection_reason`: Rejection reason
- `initiated_at`, `created_at`, `updated_at`: Timestamps

## Testing Coverage

### ✅ Unit Tests
- `CustomerServiceTest`: Service logic validation
- `KycServiceTest`: KYC workflow validation
- `CustomerControllerTest`: Controller validation
- `KycControllerTest`: KYC endpoint validation

### ✅ Integration Tests
- `CustomerProfileCrudTest`: Profile CRUD operations
- `KycVerificationFlowTest`: Complete KYC workflow
- `UserOnboardingFlowTest`: Customer auto-creation from events
- `CustomerCreationFlowIntegrationTest`: Service-level integration

## Error Handling

### ✅ Implemented Exceptions
- `CustomerNotFoundException`: Customer not found
- `MethodArgumentNotValidException`: Validation errors
- `IllegalArgumentException`: Invalid input
- `IllegalStateException`: Business rule violations (e.g., KYC already in progress)
- `AuthenticationException`: Authentication failures
- `AccessDeniedException`: Authorization failures

### ✅ Exception Handler
- `CustomerExceptionHandler`: Global exception handling
- Returns structured error responses
- HTTP status codes: 400, 401, 403, 404, 500

## Configuration

### Application Properties
- Database connection (PostgreSQL)
- Kafka bootstrap servers
- JWT issuer URI for authentication
- KYC provider configuration (if applicable)

## Missing Features

### ❌ Not Implemented
1. **Customer Deletion/Deactivation**
   - Soft delete customer records
   - Deactivation workflow
   - Data retention policies

2. **Address Management**
   - Multiple addresses per customer
   - Address validation
   - Primary address designation

3. **Document Management**
   - Document upload endpoints
   - Document storage (S3/local)
   - Document verification details
   - Document expiration tracking

4. **KYC Enhancements**
   - KYC retry mechanism
   - KYC history (multiple attempts)
   - KYC document type validation
   - KYC provider integration (real provider)

5. **Customer Search**
   - Admin search endpoint
   - Search by name, email, phone
   - Pagination and filtering

6. **Customer Analytics**
   - Customer activity tracking
   - Profile completion metrics
   - KYC conversion rates

## Verification Status

### ✅ Verified and Stable
- Customer profile creation (manual and event-driven)
- Customer profile retrieval and update
- KYC initiation workflow
- KYC status checking
- KYC webhook processing
- Event publishing and consumption
- Error handling and validation

### ⚠️ Partially Verified
- KYC provider integration (simulated via webhook)
- Customer auto-creation race conditions (handled but not extensively tested)

## Dependencies

### External Services
- **Keycloak**: User identity (via JWT)
- **Kafka**: Event streaming
- **PostgreSQL**: Data persistence

### Internal Dependencies
- **Auth Service**: User registration events

### Dependent Services
- **Wallet Service**: Consumes `CUSTOMER_CREATED` events
- **Notification Service**: Consumes `KYC_VERIFIED` events

## Integration Points

### With Auth Service
- Consumes `USER_REGISTERED` events
- Extracts userId from JWT tokens

### With Wallet Service
- Publishes `CUSTOMER_CREATED` events
- Wallet service auto-creates wallet on customer creation

### With Notification Service
- Publishes `KYC_VERIFIED` and `KYC_REJECTED` events
- Notification service sends notifications

## Next Steps

See [09_NEXT_TASKS.md](./09_NEXT_TASKS.md) for planned enhancements:
- Task: Customer Deletion/Deactivation
- Task: Document Management
- Task: Enhanced KYC Features

---

**Status**: ✅ Core MVP Complete  
**Last Updated**: 2025-12-28

