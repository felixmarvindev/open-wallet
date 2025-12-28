# Ledger Service - Current State

## Overview

The Ledger Service is the core transaction processing engine. It handles deposits, withdrawals, transfers, implements double-entry bookkeeping, enforces transaction limits, and maintains an immutable ledger of all financial transactions.

## Implemented Features

### ✅ Transaction Processing

#### 1. Create Deposit
- **Endpoint**: `POST /api/v1/transactions/deposits`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Creates deposit transaction (external → wallet)
  - Validates wallet exists and is active
  - Validates transaction limits (daily/monthly)
  - Creates double-entry ledger entries (debit CASH_ACCOUNT, credit wallet)
  - Calculates `balanceAfter` from ledger entries
  - Publishes `TRANSACTION_COMPLETED` event
  - Returns transaction details
- **Request Fields**:
  - toWalletId: Required, target wallet
  - amount: Required, must be > 0
  - currency: Optional (defaults to "KES", only KES supported)
  - idempotencyKey: Optional, for idempotent operations
- **Validation**:
  - Wallet must exist
  - Wallet must be active
  - Amount must be positive
  - Currency must be "KES" (MVP)
  - Daily/monthly limits must not be exceeded
- **Integration Tests**: ✅ Covered in `TransactionLimitValidationTest`, `BalanceUpdateFlowTest`

#### 2. Create Withdrawal
- **Endpoint**: `POST /api/v1/transactions/withdrawals`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Creates withdrawal transaction (wallet → external)
  - Validates wallet exists and is active
  - Validates transaction limits (daily/monthly)
  - Creates double-entry ledger entries (debit wallet, credit CASH_ACCOUNT)
  - Calculates `balanceAfter` from ledger entries
  - Publishes `TRANSACTION_COMPLETED` event
  - Returns transaction details
- **Request Fields**:
  - fromWalletId: Required, source wallet
  - amount: Required, must be > 0
  - currency: Optional (defaults to "KES")
  - idempotencyKey: Optional
- **Validation**: Same as deposit
- **Integration Tests**: ✅ Covered in `BalanceUpdateFlowTest`

#### 3. Create Transfer
- **Endpoint**: `POST /api/v1/transactions/transfers`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Creates transfer transaction (wallet → wallet)
  - Validates both wallets exist and are active
  - Validates fromWalletId != toWalletId
  - Validates transaction limits for both wallets
  - Creates double-entry ledger entries atomically:
    - Debit fromWalletId
    - Credit toWalletId
  - Calculates `balanceAfter` for both wallets
  - Publishes `TRANSACTION_COMPLETED` event
  - Returns transaction details
- **Request Fields**:
  - fromWalletId: Required, source wallet
  - toWalletId: Required, destination wallet
  - amount: Required, must be > 0
  - currency: Optional (defaults to "KES")
  - idempotencyKey: Optional
- **Validation**: Same as deposit/withdrawal, plus wallet uniqueness
- **Integration Tests**: ✅ Covered in `BalanceUpdateFlowTest`

#### 4. Get Transaction
- **Endpoint**: `GET /api/v1/transactions/{id}`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Retrieves transaction by ID
  - Returns transaction details with status
  - Accessible by USER, ADMIN, AUDITOR roles
- **Response Fields**:
  - id, transactionType, amount, currency
  - fromWalletId, toWalletId
  - status, initiatedAt, completedAt
  - failureReason, idempotencyKey, metadata
- **Integration Tests**: ✅ Covered in unit tests

### ✅ Double-Entry Bookkeeping

#### Ledger Entry Creation
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Every transaction creates two ledger entries (debit and credit)
  - Entries are immutable (insert-only)
  - Each entry includes:
    - accountType: WALLET or CASH_ACCOUNT
    - entryType: DEBIT or CREDIT
    - amount: Transaction amount
    - balanceBefore: Balance before this entry
    - balanceAfter: Balance after this entry (calculated from ledger)
    - walletId: Wallet ID (if accountType is WALLET)
    - transactionId: Reference to transaction
- **Balance Calculation**:
  - `balanceBefore`: Calculated from previous ledger entries
  - `balanceAfter`: `balanceBefore + amount` (for CREDIT) or `balanceBefore - amount` (for DEBIT)
- **Integration Tests**: ✅ Verified in transaction tests

#### Ledger Entry Queries
- **Endpoint**: `GET /api/v1/ledger-entries/wallet/{walletId}`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Returns all ledger entries for a wallet
  - Ordered by creation time
  - Used for audit and reconciliation
- **Access**: USER, ADMIN, AUDITOR roles

- **Endpoint**: `GET /api/v1/ledger-entries/wallet/{walletId}/balance`
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Calculates balance from ledger entries
  - Formula: Sum(CREDITS) - Sum(DEBITS)
  - Used for balance reconciliation
- **Access**: USER, ADMIN, AUDITOR roles

### ✅ Transaction Limits

#### Limit Validation
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Validates daily and monthly transaction limits
  - Limits retrieved from wallets table (read-only access)
  - Current usage calculated from completed transactions
  - Validates before transaction creation
  - Throws `LimitExceededException` if limit exceeded
- **Limit Types**:
  - Daily limit: Sum of transactions in current day
  - Monthly limit: Sum of transactions in current month
- **Calculation**:
  - Uses `TransactionRepository.sumTransactionAmountsByWalletAndDateRange()`
  - Includes both fromWalletId and toWalletId transactions
  - Only counts COMPLETED transactions
  - Uses Clock abstraction for time-based calculations (testable)
- **Integration Tests**: ✅ Covered in `TransactionLimitValidationTest`

#### Limit Service
- **Service**: `TransactionLimitService`
- **Dependencies**:
  - `WalletLimitsService`: Reads wallet limits from wallets table
  - `TransactionRepository`: Calculates current usage
  - `Clock`: Time abstraction for testing
- **Status**: ✅ Implemented and Verified

### ✅ Idempotency

#### Idempotency Key Support
- **Status**: ✅ Implemented and Verified
- **Functionality**:
  - Transactions can include `idempotencyKey`
  - If transaction with same key exists, returns existing transaction
  - Prevents duplicate transaction processing
  - Key stored in database with UNIQUE constraint
- **Use Cases**:
  - Retry scenarios
  - Webhook retries
  - Client-side duplicate prevention

### ✅ Transaction Status Management

#### Status Lifecycle
- **Statuses**:
  - `PENDING`: Transaction created, not yet processed
  - `COMPLETED`: Transaction processed successfully
  - `FAILED`: Transaction failed (error during processing)
  - `CANCELLED`: Transaction cancelled (not implemented)
- **Status Transitions**:
  - PENDING → COMPLETED (on successful processing)
  - PENDING → FAILED (on error)
  - FAILED → (no transitions, terminal state)
- **Error Handling**:
  - Failed transactions set `failureReason`
  - Failed transactions publish `TRANSACTION_FAILED` event
  - Transaction status updated atomically

### ✅ Event Publishing

#### Kafka Events
- **Topic**: `transaction-events`
- **Events Published**:
  - `TRANSACTION_INITIATED`: When transaction is created
  - `TRANSACTION_COMPLETED`: When transaction completes successfully
  - `TRANSACTION_FAILED`: When transaction fails
- **Event Schema**: Includes transactionId, transactionType, amount, fromWalletId, toWalletId, status, timestamp
- **Status**: ✅ Implemented and Verified

## Architecture

### Service Components

```
TransactionController
  ├── TransactionService
  │   ├── TransactionRepository
  │   ├── LedgerEntryRepository
  │   ├── LedgerEntryService
  │   ├── TransactionLimitService
  │   │   └── WalletLimitsService (read-only wallet access)
  │   └── TransactionEventProducer
  ├── LedgerEntryController
  │   └── LedgerEntryService
  └── TransactionExceptionHandler
```

### Database Schema

#### Transactions Table
- `id`: Primary key
- `transaction_type`: Enum (DEPOSIT, WITHDRAWAL, TRANSFER)
- `amount`: Decimal(19,2), CHECK (amount > 0)
- `currency`: VARCHAR(3), default 'KES'
- `from_wallet_id`: Foreign key to wallets (nullable)
- `to_wallet_id`: Foreign key to wallets (nullable)
- `status`: Enum (PENDING, COMPLETED, FAILED, CANCELLED)
- `initiated_at`: Timestamp
- `completed_at`: Timestamp (nullable)
- `failure_reason`: TEXT (nullable)
- `idempotency_key`: VARCHAR(255), UNIQUE
- `metadata`: JSONB
- `created_at`, `updated_at`: Timestamps
- **Indexes**: from_wallet_id, to_wallet_id, status, initiated_at, idempotency_key

#### Ledger Entries Table
- `id`: Primary key
- `account_type`: Enum (WALLET, CASH_ACCOUNT)
- `entry_type`: Enum (DEBIT, CREDIT)
- `amount`: Decimal(19,2)
- `balance_before`: Decimal(19,2)
- `balance_after`: Decimal(19,2)
- `wallet_id`: Foreign key to wallets (nullable for CASH_ACCOUNT)
- `transaction_id`: Foreign key to transactions
- `created_at`: Timestamp
- **Indexes**: wallet_id, transaction_id, created_at

## Testing Coverage

### ✅ Unit Tests
- `TransactionServiceUnitTest`: Service logic with mocked dependencies
- `TransactionServiceTest`: Service with real database (@DataJpaTest)
- `LedgerEntryServiceTest`: Ledger entry calculations
- `TransactionLimitServiceTest`: Limit validation logic

### ✅ Integration Tests
- `TransactionLimitValidationTest`: End-to-end limit validation
- `BalanceUpdateFlowTest`: Transaction processing and balance updates
- `BalanceReconciliationTest`: Balance reconciliation from ledger

## Error Handling

### ✅ Implemented Exceptions
- `TransactionNotFoundException`: Transaction not found
- `LimitExceededException`: Transaction limit exceeded
- `WalletNotFoundException`: Wallet not found (during limit validation)
- `MethodArgumentNotValidException`: Validation errors
- `IllegalArgumentException`: Invalid input
- `IllegalStateException`: Business rule violations
- `AuthenticationException`: Authentication failures
- `AccessDeniedException`: Authorization failures

### ✅ Exception Handler
- `TransactionExceptionHandler`: Global exception handling
- Returns structured error responses
- HTTP status codes: 400, 401, 403, 404, 500

## Configuration

### Application Properties
- Database connection (PostgreSQL)
- Kafka bootstrap servers
- JWT issuer URI for authentication
- Clock configuration (for time-based operations)

## Missing Features

### ❌ Not Implemented
1. **Transaction History/Listing**
   - Get transactions by wallet (`GET /api/v1/transactions?walletId={id}`)
   - Get transactions by date range
   - Get transactions by status
   - Transaction pagination and filtering
   - Transaction search

2. **Transaction Cancellation**
   - Cancel pending transaction (`POST /api/v1/transactions/{id}/cancel`)
   - Cancellation workflow
   - Refund processing

3. **Transaction Reversal/Refund**
   - Reverse completed transaction
   - Refund processing
   - Reversal ledger entries

4. **Bulk Transactions**
   - Batch transaction creation
   - Bulk processing
   - Batch validation

5. **Transaction Fees**
   - Fee calculation
   - Fee ledger entries
   - Fee configuration

6. **Pending Transaction Management**
   - Timeout handling for pending transactions
   - Automatic cleanup of stale pending transactions
   - Pending transaction status updates

7. **Transaction Export**
   - Export transactions to CSV/Excel
   - Transaction reports
   - Audit trail export

8. **Advanced Validation**
   - Minimum transaction amount
   - Maximum transaction amount
   - Transaction velocity checks
   - Suspicious transaction detection

## Verification Status

### ✅ Verified and Stable
- Deposit transaction processing
- Withdrawal transaction processing
- Transfer transaction processing
- Double-entry bookkeeping
- Balance calculation from ledger
- Transaction limit validation (daily/monthly)
- Idempotency handling
- Event publishing
- Error handling and validation
- Time-based limit calculations (Clock abstraction)

### ⚠️ Partially Verified
- High-concurrency transaction processing (not load tested)
- Transaction failure recovery (basic error handling)

## Dependencies

### External Services
- **Keycloak**: User authentication (via JWT)
- **Kafka**: Event streaming
- **PostgreSQL**: Data persistence

### Internal Dependencies
- **Wallet Service**: Reads wallet limits (read-only database access)

### Dependent Services
- **Wallet Service**: Consumes `TRANSACTION_COMPLETED` events
- **Notification Service**: Consumes transaction events

## Integration Points

### With Wallet Service
- Reads wallet limits for transaction validation (native SQL queries)
- Publishes `TRANSACTION_COMPLETED` events
- Wallet service updates balances on transaction completion

### With Notification Service
- Publishes transaction events for notifications

## Performance Considerations

### Optimizations Implemented
- Database indexes on frequently queried fields
- Efficient balance calculation from ledger entries
- Idempotency to prevent duplicate processing
- Transaction batching in database operations

### Known Limitations
- Balance calculation queries all ledger entries (could be optimized with balance snapshots)
- No distributed transaction coordination (Saga pattern not implemented)
- Transaction history queries not optimized (missing pagination)

## Next Steps

See [09_NEXT_TASKS.md](./09_NEXT_TASKS.md) for planned enhancements:
- Task: Transaction History Endpoints
- Task: Transaction Cancellation
- Task: Transaction Fees

---

**Status**: ✅ Core MVP Complete  
**Last Updated**: 2025-12-28

