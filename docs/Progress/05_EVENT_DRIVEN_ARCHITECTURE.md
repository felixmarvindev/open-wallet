# Event-Driven Architecture - Current State

## Overview

The OpenWallet platform uses Apache Kafka for asynchronous, event-driven communication between microservices. This enables loose coupling, scalability, and eventual consistency across services.

## Event Flow Architecture

### Event Flow Diagram

```
Auth Service
  └── USER_REGISTERED ──┐
                        │
                        ▼
                    Kafka (user-events)
                        │
                        ▼
Customer Service ── CUSTOMER_CREATED ──┐
                                       │
                                       ▼
                                   Kafka (customer-events)
                                       │
                                       ▼
Wallet Service ── WALLET_CREATED ──┐
                                   │
                                   ▼
                               Kafka (wallet-events)

Customer Service ── KYC_VERIFIED ──┐
                                   │
                                   ▼
                               Kafka (kyc-events)
                                   │
                                   ▼
                           Notification Service

Ledger Service ── TRANSACTION_COMPLETED ──┐
                                          │
                                          ▼
                                      Kafka (transaction-events)
                                          │
                    ┌────────────────────┴────────────────────┐
                    ▼                                          ▼
            Wallet Service                              Notification Service
```

## Implemented Event Flows

### ✅ User Registration Flow

#### Event: USER_REGISTERED
- **Producer**: Auth Service
- **Topic**: `user-events`
- **Consumer**: Customer Service
- **Status**: ✅ Implemented and Verified

**Flow**:
1. User registers via Auth Service
2. Auth Service creates user in Keycloak
3. Auth Service publishes `USER_REGISTERED` event
4. Customer Service consumes event
5. Customer Service auto-creates customer profile
6. Customer Service publishes `CUSTOMER_CREATED` event

**Event Schema**:
```json
{
  "eventType": "USER_REGISTERED",
  "eventId": "uuid",
  "userId": "keycloak-user-id",
  "username": "john_doe",
  "email": "john@example.com",
  "timestamp": "2025-12-28T10:00:00Z",
  "metadata": {
    "source": "auth-service",
    "version": "1.0"
  }
}
```

**Integration Tests**: ✅ Covered in `UserOnboardingFlowTest`

### ✅ Customer Creation Flow

#### Event: CUSTOMER_CREATED
- **Producer**: Customer Service
- **Topic**: `customer-events`
- **Consumer**: Wallet Service
- **Status**: ✅ Implemented and Verified

**Flow**:
1. Customer profile created (manual or event-driven)
2. Customer Service publishes `CUSTOMER_CREATED` event
3. Wallet Service consumes event
4. Wallet Service auto-creates wallet with default limits
5. Wallet Service publishes `WALLET_CREATED` event

**Event Schema**:
```json
{
  "eventType": "CUSTOMER_CREATED",
  "eventId": "uuid",
  "customerId": 123,
  "userId": "keycloak-user-id",
  "timestamp": "2025-12-28T10:00:00Z",
  "metadata": {
    "source": "customer-service",
    "version": "1.0"
  }
}
```

**Integration Tests**: ✅ Covered in `WalletCreationFlowTest`, `UserOnboardingFlowTest`

### ✅ Transaction Completion Flow

#### Event: TRANSACTION_COMPLETED
- **Producer**: Ledger Service
- **Topic**: `transaction-events`
- **Consumers**: Wallet Service, Notification Service
- **Status**: ✅ Implemented and Verified

**Flow**:
1. Transaction created in Ledger Service
2. Double-entry ledger entries created
3. Transaction status set to COMPLETED
4. Ledger Service publishes `TRANSACTION_COMPLETED` event
5. Wallet Service consumes event
6. Wallet Service updates wallet balance
7. Wallet Service invalidates/refreshes Redis cache
8. Notification Service consumes event
9. Notification Service sends notification (SMS/email)

**Event Schema**:
```json
{
  "eventType": "TRANSACTION_COMPLETED",
  "eventId": "uuid",
  "transactionId": 456,
  "transactionType": "DEPOSIT",
  "amount": "1000.00",
  "currency": "KES",
  "fromWalletId": null,
  "toWalletId": 789,
  "status": "COMPLETED",
  "timestamp": "2025-12-28T10:00:00Z",
  "metadata": {
    "source": "ledger-service",
    "version": "1.0"
  }
}
```

**Integration Tests**: ✅ Covered in `BalanceUpdateFlowTest`

### ✅ KYC Verification Flow

#### Event: KYC_VERIFIED
- **Producer**: Customer Service
- **Topic**: `kyc-events`
- **Consumer**: Notification Service
- **Status**: ✅ Implemented and Verified

**Flow**:
1. KYC webhook received in Customer Service
2. KYC status updated to VERIFIED
3. Customer Service publishes `KYC_VERIFIED` event
4. Notification Service consumes event
5. Notification Service sends verification notification

**Event Schema**:
```json
{
  "eventType": "KYC_VERIFIED",
  "eventId": "uuid",
  "customerId": 123,
  "kycCheckId": 456,
  "timestamp": "2025-12-28T10:00:00Z",
  "metadata": {
    "source": "customer-service",
    "version": "1.0"
  }
}
```

**Integration Tests**: ✅ Covered in `KycVerificationFlowTest`

## Kafka Topics

### ✅ Implemented Topics

| Topic | Producer(s) | Consumer(s) | Event Types | Status |
|-------|------------|-------------|-------------|--------|
| `user-events` | Auth Service | Customer Service | USER_REGISTERED, USER_LOGIN, USER_LOGOUT | ✅ |
| `customer-events` | Customer Service | Wallet Service | CUSTOMER_CREATED | ✅ |
| `wallet-events` | Wallet Service | (Future consumers) | WALLET_CREATED | ✅ |
| `kyc-events` | Customer Service | Notification Service | KYC_VERIFIED, KYC_REJECTED, KYC_INITIATED | ✅ |
| `transaction-events` | Ledger Service | Wallet Service, Notification Service | TRANSACTION_COMPLETED, TRANSACTION_FAILED, TRANSACTION_INITIATED | ✅ |

## Event Producers

### ✅ Auth Service - UserEventProducer
- **Topic**: `user-events`
- **Events**: USER_REGISTERED, USER_LOGIN, USER_LOGOUT
- **Status**: ✅ Implemented

### ✅ Customer Service - CustomerEventProducer
- **Topic**: `customer-events`
- **Events**: CUSTOMER_CREATED
- **Status**: ✅ Implemented

### ✅ Customer Service - KycEventProducer
- **Topic**: `kyc-events`
- **Events**: KYC_VERIFIED, KYC_REJECTED, KYC_INITIATED
- **Status**: ✅ Implemented

### ✅ Wallet Service - WalletEventProducer
- **Topic**: `wallet-events`
- **Events**: WALLET_CREATED
- **Status**: ✅ Implemented

### ✅ Ledger Service - TransactionEventProducer
- **Topic**: `transaction-events`
- **Events**: TRANSACTION_COMPLETED, TRANSACTION_FAILED, TRANSACTION_INITIATED
- **Status**: ✅ Implemented

## Event Consumers

### ✅ Customer Service - UserEventListener
- **Topic**: `user-events`
- **Event**: USER_REGISTERED
- **Action**: Auto-create customer profile
- **Status**: ✅ Implemented and Verified

### ✅ Wallet Service - CustomerEventListener
- **Topic**: `customer-events`
- **Event**: CUSTOMER_CREATED
- **Action**: Auto-create wallet with default limits
- **Status**: ✅ Implemented and Verified

### ✅ Wallet Service - TransactionEventListener
- **Topic**: `transaction-events`
- **Event**: TRANSACTION_COMPLETED
- **Action**: Update wallet balance, refresh cache
- **Status**: ✅ Implemented and Verified

### ✅ Notification Service - Event Listeners
- **Topics**: `kyc-events`, `transaction-events`
- **Events**: KYC_VERIFIED, KYC_REJECTED, TRANSACTION_COMPLETED
- **Action**: Send notifications (SMS/email simulation)
- **Status**: ✅ Implemented (basic)

## Event Schema and Serialization

### Event Structure
All events follow a common structure:
- `eventType`: String (event type identifier)
- `eventId`: UUID (unique event identifier)
- `timestamp`: ISO 8601 timestamp
- `metadata`: Map (source service, version, etc.)
- Service-specific fields (userId, customerId, transactionId, etc.)

### Serialization
- **Format**: JSON
- **Library**: Jackson (Spring Kafka default)
- **Status**: ✅ Implemented

## Event Processing Guarantees

### ✅ At-Least-Once Delivery
- **Status**: ✅ Implemented
- **Mechanism**: Kafka consumer acknowledgment
- **Handling**: Idempotent event processing

### ✅ Idempotency
- **Status**: ✅ Implemented
- **Mechanism**: Event ID tracking, idempotent operations
- **Examples**:
  - Customer creation: Checks if customer already exists
  - Wallet creation: Checks if wallet already exists
  - Transaction processing: Uses idempotency keys

### ⚠️ Exactly-Once Delivery
- **Status**: ❌ Not Implemented
- **Reason**: Requires Kafka transactions (not configured)
- **Impact**: Low (idempotency handles duplicates)

### ⚠️ Event Ordering
- **Status**: ⚠️ Partial
- **Mechanism**: Kafka partition key ensures ordering per key
- **Limitation**: No global ordering across partitions
- **Impact**: Low (per-entity ordering sufficient)

## Error Handling

### ✅ Consumer Error Handling
- **Status**: ✅ Implemented
- **Mechanism**:
  - Try-catch in event listeners
  - Exception logging
  - Re-throw for Kafka retry
- **Retry Strategy**: Kafka consumer retry (default)

### ❌ Dead Letter Queue (DLQ)
- **Status**: ❌ Not Implemented
- **Impact**: Failed events may be lost or retried indefinitely
- **Recommendation**: Implement DLQ for production

### ❌ Event Replay
- **Status**: ❌ Not Implemented
- **Impact**: Cannot replay events for recovery
- **Recommendation**: Implement event replay mechanism

## Testing Coverage

### ✅ Integration Tests
- `UserOnboardingFlowTest`: User registration → Customer → Wallet
- `WalletCreationFlowTest`: Customer creation → Wallet creation
- `BalanceUpdateFlowTest`: Transaction → Balance update
- `KycVerificationFlowTest`: KYC verification → Notification

### ✅ Event Verification
- Kafka event publishing verified
- Event consumption verified
- Event processing verified
- Idempotency verified

## Configuration

### Kafka Configuration
- **Bootstrap Servers**: Configurable via application properties
- **Consumer Groups**: Per-service consumer groups
- **Auto-Offset Reset**: `earliest` (for tests), `latest` (for production)
- **Serialization**: JSON (Jackson)

### Topic Configuration
- **Partitions**: Default (1 partition per topic)
- **Replication**: Default (1 replica)
- **Retention**: Default (7 days)

## Missing Features

### ❌ Not Implemented
1. **Dead Letter Queue (DLQ)**
   - Failed event handling
   - DLQ topic configuration
   - DLQ monitoring and alerting

2. **Event Replay**
   - Event replay mechanism
   - Event replay API
   - Event replay validation

3. **Event Versioning**
   - Event schema versioning
   - Backward compatibility
   - Schema evolution

4. **Event Ordering Guarantees**
   - Global event ordering
   - Cross-partition ordering
   - Ordering validation

5. **Event Monitoring**
   - Event publishing metrics
   - Event consumption metrics
   - Event lag monitoring
   - Event processing time

6. **Event Sourcing**
   - Event store
   - Event replay from store
   - Event-based state reconstruction

7. **Saga Pattern**
   - Distributed transaction coordination
   - Saga orchestration
   - Compensation transactions

## Verification Status

### ✅ Verified and Stable
- Event publishing to Kafka
- Event consumption from Kafka
- Event processing logic
- Idempotent event handling
- Event-driven service communication
- Error handling in consumers

### ⚠️ Partially Verified
- Event ordering (per-partition only)
- Event replay (not implemented)
- DLQ handling (not implemented)

## Dependencies

### External Services
- **Kafka**: Event streaming platform
- **Zookeeper**: Kafka coordination (if applicable)

### Internal Dependencies
- All services depend on Kafka for event communication

## Performance Considerations

### Current Implementation
- Single partition per topic (limits parallelism)
- No batching of events
- Synchronous event processing

### Optimization Opportunities
- Increase topic partitions for parallelism
- Implement event batching
- Async event processing where possible
- Event compression

## Next Steps

See [09_NEXT_TASKS.md](./09_NEXT_TASKS.md) for planned enhancements:
- Task: Dead Letter Queue Implementation
- Task: Event Replay Mechanism
- Task: Event Monitoring and Metrics

---

**Status**: ✅ Core MVP Complete  
**Last Updated**: 2025-12-28

