# End-to-End Flows - Current State

## Overview

This document describes the complete user journeys and end-to-end flows that are implemented and verified in the OpenWallet platform. Each flow represents a complete business process from start to finish.

## Implemented Flows

### ✅ Flow 1: User Onboarding

#### Description
Complete user registration and onboarding flow from registration to wallet creation.

#### Steps
1. **User Registration**
   - User calls `POST /api/v1/auth/register`
   - Auth Service creates user in Keycloak
   - Auth Service publishes `USER_REGISTERED` event

2. **Customer Auto-Creation**
   - Customer Service consumes `USER_REGISTERED` event
   - Customer Service auto-creates customer profile
   - Customer Service publishes `CUSTOMER_CREATED` event

3. **Wallet Auto-Creation**
   - Wallet Service consumes `CUSTOMER_CREATED` event
   - Wallet Service auto-creates wallet with default limits
   - Wallet Service publishes `WALLET_CREATED` event

#### Result
- User registered and authenticated
- Customer profile created
- Wallet created and ready for transactions

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `UserOnboardingFlowTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 2: KYC Verification

#### Description
Complete KYC verification workflow from initiation to verification.

#### Steps
1. **KYC Initiation**
   - User calls `POST /api/v1/customers/me/kyc/initiate`
   - Customer Service creates KYC check with status `IN_PROGRESS`
   - Customer Service publishes `KYC_INITIATED` event

2. **External KYC Processing**
   - External KYC provider processes documents
   - External provider calls webhook: `POST /api/v1/customers/kyc/webhook`

3. **KYC Verification**
   - Customer Service receives webhook
   - Customer Service updates KYC status to `VERIFIED` or `REJECTED`
   - Customer Service publishes `KYC_VERIFIED` or `KYC_REJECTED` event

4. **Notification**
   - Notification Service consumes KYC event
   - Notification Service sends verification notification

#### Result
- KYC status updated
- Customer notified of verification result

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `KycVerificationFlowTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 3: Deposit Transaction

#### Description
Complete deposit flow from transaction creation to balance update.

#### Steps
1. **Transaction Creation**
   - User calls `POST /api/v1/transactions/deposits`
   - Ledger Service validates wallet exists and is active
   - Ledger Service validates transaction limits (daily/monthly)
   - Ledger Service creates transaction with status `PENDING`

2. **Double-Entry Processing**
   - Ledger Service creates ledger entries:
     - Debit: CASH_ACCOUNT
     - Credit: Wallet
   - Ledger Service calculates `balanceAfter` from ledger entries
   - Transaction status set to `COMPLETED`
   - Ledger Service publishes `TRANSACTION_COMPLETED` event

3. **Balance Update**
   - Wallet Service consumes `TRANSACTION_COMPLETED` event
   - Wallet Service updates wallet balance (increases)
   - Wallet Service invalidates/refreshes Redis cache

4. **Notification**
   - Notification Service consumes transaction event
   - Notification Service sends deposit notification

#### Result
- Transaction recorded in ledger
- Wallet balance updated
- User notified of deposit

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `BalanceUpdateFlowTest`, `TransactionLimitValidationTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 4: Withdrawal Transaction

#### Description
Complete withdrawal flow from transaction creation to balance update.

#### Steps
1. **Transaction Creation**
   - User calls `POST /api/v1/transactions/withdrawals`
   - Ledger Service validates wallet exists and is active
   - Ledger Service validates transaction limits (daily/monthly)
   - Ledger Service creates transaction with status `PENDING`

2. **Double-Entry Processing**
   - Ledger Service creates ledger entries:
     - Debit: Wallet
     - Credit: CASH_ACCOUNT
   - Ledger Service calculates `balanceAfter` from ledger entries
   - Transaction status set to `COMPLETED`
   - Ledger Service publishes `TRANSACTION_COMPLETED` event

3. **Balance Update**
   - Wallet Service consumes `TRANSACTION_COMPLETED` event
   - Wallet Service updates wallet balance (decreases)
   - Wallet Service validates balance doesn't go negative
   - Wallet Service invalidates/refreshes Redis cache

4. **Notification**
   - Notification Service consumes transaction event
   - Notification Service sends withdrawal notification

#### Result
- Transaction recorded in ledger
- Wallet balance updated
- User notified of withdrawal

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `BalanceUpdateFlowTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 5: Transfer Transaction

#### Description
Complete transfer flow between two wallets.

#### Steps
1. **Transaction Creation**
   - User calls `POST /api/v1/transactions/transfers`
   - Ledger Service validates both wallets exist and are active
   - Ledger Service validates fromWalletId != toWalletId
   - Ledger Service validates transaction limits for both wallets
   - Ledger Service creates transaction with status `PENDING`

2. **Double-Entry Processing**
   - Ledger Service creates ledger entries atomically:
     - Debit: fromWalletId
     - Credit: toWalletId
   - Ledger Service calculates `balanceAfter` for both wallets
   - Transaction status set to `COMPLETED`
   - Ledger Service publishes `TRANSACTION_COMPLETED` event

3. **Balance Updates**
   - Wallet Service consumes `TRANSACTION_COMPLETED` event
   - Wallet Service updates fromWalletId balance (decreases)
   - Wallet Service updates toWalletId balance (increases)
   - Wallet Service invalidates/refreshes Redis cache for both wallets

4. **Notifications**
   - Notification Service consumes transaction event
   - Notification Service sends notifications to both parties

#### Result
- Transaction recorded in ledger
- Both wallet balances updated
- Both users notified of transfer

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `BalanceUpdateFlowTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 6: Balance Reconciliation

#### Description
Balance reconciliation flow to verify wallet balance matches ledger.

#### Steps
1. **Reconciliation Request**
   - User/Admin calls `GET /api/v1/wallets/{id}/balance/reconcile`
   - Wallet Service retrieves wallet balance from database

2. **Ledger Balance Calculation**
   - Wallet Service queries ledger entries for wallet
   - Wallet Service calculates balance: Sum(CREDITS) - Sum(DEBITS)

3. **Comparison**
   - Wallet Service compares wallet balance with ledger balance
   - Wallet Service returns reconciliation result

#### Result
- Reconciliation result with discrepancy (if any)
- Verification of data consistency

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `BalanceReconciliationTest`
- **Verification**: ✅ All steps verified

---

### ✅ Flow 7: Transaction Limit Validation

#### Description
Transaction limit validation flow for daily and monthly limits.

#### Steps
1. **Transaction Request**
   - User calls transaction endpoint (deposit/withdrawal/transfer)
   - Ledger Service receives request

2. **Limit Retrieval**
   - Ledger Service queries wallet limits from wallets table
   - Ledger Service retrieves dailyLimit and monthlyLimit

3. **Usage Calculation**
   - Ledger Service calculates current usage:
     - Daily: Sum of transactions in current day
     - Monthly: Sum of transactions in current month
   - Ledger Service uses Clock abstraction for time-based calculations

4. **Validation**
   - Ledger Service validates: (currentUsage + amount) <= limit
   - If limit exceeded, throws `LimitExceededException`
   - If within limit, proceeds with transaction

#### Result
- Transaction allowed or rejected based on limits
- Appropriate error message if limit exceeded

#### Status
- **Implementation**: ✅ Complete
- **Testing**: ✅ Covered in `TransactionLimitValidationTest`
- **Verification**: ✅ All steps verified

---

## Flow Diagrams

### User Onboarding Flow
```
User → Auth Service → Keycloak
         ↓
    USER_REGISTERED event
         ↓
Customer Service → Customer Created
         ↓
    CUSTOMER_CREATED event
         ↓
Wallet Service → Wallet Created
```

### Transaction Flow
```
User → Ledger Service → Transaction Created
         ↓
    Double-Entry Ledger Entries
         ↓
    TRANSACTION_COMPLETED event
         ↓
Wallet Service → Balance Updated
         ↓
Notification Service → Notification Sent
```

## Integration Test Coverage

### ✅ Covered Flows
- `UserOnboardingFlowTest`: User registration → Customer → Wallet
- `KycVerificationFlowTest`: KYC initiation → Verification → Notification
- `BalanceUpdateFlowTest`: Transaction → Balance update
- `BalanceReconciliationTest`: Balance reconciliation
- `TransactionLimitValidationTest`: Limit validation
- `WalletCrudTest`: Wallet CRUD operations
- `CustomerProfileCrudTest`: Customer CRUD operations

## Error Scenarios

### ✅ Handled Errors
- Invalid credentials (login)
- Duplicate registration
- Wallet not found
- Insufficient balance
- Transaction limit exceeded
- Invalid transaction data
- KYC already in progress
- Customer not found

### ✅ Error Flow
1. Error occurs in service
2. Exception thrown
3. Exception handler catches exception
4. Structured error response returned
5. Error logged with correlation ID

## Missing Flows

### ❌ Not Implemented
1. **Password Reset Flow**
   - Forgot password request
   - Password reset token generation
   - Password reset completion

2. **Transaction History Flow**
   - Get transaction history
   - Filter and paginate transactions
   - Export transactions

3. **Wallet Lifecycle Flow**
   - Wallet suspension
   - Wallet activation
   - Wallet closure

4. **Transaction Cancellation Flow**
   - Cancel pending transaction
   - Refund processing
   - Reversal ledger entries

5. **Multi-Wallet Transfer Flow**
   - Transfer between user's own wallets
   - Transfer validation
   - Balance updates

## Verification Status

### ✅ Verified Flows
- User onboarding (registration → customer → wallet)
- KYC verification (initiation → verification → notification)
- Deposit transaction (creation → processing → balance update)
- Withdrawal transaction (creation → processing → balance update)
- Transfer transaction (creation → processing → balance updates)
- Balance reconciliation (reconciliation → discrepancy detection)
- Transaction limit validation (daily/monthly limits)

### ⚠️ Partially Verified
- Error recovery flows (basic error handling, not all scenarios)
- High-concurrency flows (not load tested)

## Performance Characteristics

### Current Performance
- User onboarding: ~2-3 seconds (event-driven)
- Transaction processing: ~500ms-1s (synchronous + async)
- Balance update: ~100-200ms (event-driven)
- Balance reconciliation: ~200-500ms (depends on ledger entries)

### Optimization Opportunities
- Parallel event processing
- Batch balance updates
- Cached limit lookups
- Optimized ledger queries

## Next Steps

See [09_NEXT_TASKS.md](./09_NEXT_TASKS.md) for planned enhancements:
- Task: Transaction History Flow
- Task: Wallet Lifecycle Flow
- Task: Password Reset Flow

---

**Status**: ✅ Core MVP Flows Complete  
**Last Updated**: 2025-12-28

