# Transaction Flow and Limit Validation

## Current Transaction Flow

```
1. Client Request
   ↓
2. TransactionService.createDeposit/Withdrawal/Transfer()
   ├─ validateAmount()
   ├─ validateRequest()
   ├─ checkIdempotency()
   ├─ create Transaction (status: PENDING)
   ├─ publish TRANSACTION_INITIATED event
   ├─ createDoubleEntry() ← Creates LedgerEntry with placeholder balanceAfter
   ├─ update Transaction (status: COMPLETED)
   ├─ publish TRANSACTION_COMPLETED event → Kafka
   └─ return TransactionResponse
   
3. WalletService.TransactionEventListener (async)
   ├─ Receives TRANSACTION_COMPLETED event
   ├─ Calls WalletService.updateBalanceFromTransaction()
   └─ Updates wallet.balance in database + cache
```

## Where Limit Validation Should Go

**Limit validation should happen BEFORE creating the transaction**, right after idempotency check:

```java
@Transactional
public TransactionResponse createDeposit(DepositRequest request) {
    validateAmount(request.getAmount());
    validateDepositRequest(request);
    String currency = normalizeCurrency(request.getCurrency());

    // 1. Check idempotency
    Transaction existing = transactionRepository.findByIdempotencyKey(...);
    if (existing != null) {
        return toResponse(existing);
    }

    // 2. ✅ VALIDATE LIMITS HERE (NEW)
    transactionLimitService.validateTransactionLimits(
        request.getToWalletId(), 
        request.getAmount(), 
        TransactionType.DEPOSIT
    );

    // 3. Create transaction (only if limits pass)
    Transaction tx = Transaction.builder()...
    // ... rest of flow
}
```

## How Wallet Balance Changes After Transaction

### Current Implementation (Asynchronous via Kafka)

1. **Ledger Service** creates transaction and ledger entries
2. **Ledger Service** publishes `TRANSACTION_COMPLETED` event to Kafka
3. **Wallet Service** listens to event (TransactionEventListener)
4. **Wallet Service** updates `wallets.balance` in database
5. **Wallet Service** refreshes Redis cache

### The `balanceAfter` Field Issue ✅ FIXED

The `balanceAfter` field in `LedgerEntry` was previously a placeholder. It now correctly represents the wallet balance **after** this ledger entry.

**Previous (Wrong):**
```java
BigDecimal debitBalance = amount;  // Just the transaction amount
BigDecimal creditBalance = amount;  // Just the transaction amount
```

**Current Implementation (Fixed):**
```java
// Calculate current balance from previous ledger entries BEFORE creating new entries
BigDecimal balanceBefore = ledgerEntryService.calculateBalanceFromLedger(walletId);

// For DEBIT: balanceAfter = balanceBefore - amount
// For CREDIT: balanceAfter = balanceBefore + amount
```

The fix calculates the actual balance from all previous ledger entries, ensuring `balanceAfter` accurately reflects the wallet balance after each transaction.

## Solution: Fix balanceAfter Calculation

### Option 1: Calculate from Previous Ledger Entries (Recommended)

Since we already have `LedgerEntryService.calculateBalanceFromLedger()`, we can use it:

```java
private void createDoubleEntry(Transaction tx, Long fromWalletId, Long toWalletId, BigDecimal amount) {
    BigDecimal fromWalletBalanceAfter = null;
    BigDecimal toWalletBalanceAfter = null;

    // Calculate current balances from ledger entries
    if (fromWalletId != null) {
        BigDecimal currentBalance = ledgerEntryService.calculateBalanceFromLedger(fromWalletId);
        fromWalletBalanceAfter = currentBalance.subtract(amount); // DEBIT: decrease
    }

    if (toWalletId != null) {
        BigDecimal currentBalance = ledgerEntryService.calculateBalanceFromLedger(toWalletId);
        toWalletBalanceAfter = currentBalance.add(amount); // CREDIT: increase
    }

    // Create ledger entries with correct balanceAfter
    if (fromWalletId != null) {
        LedgerEntry debit = LedgerEntry.builder()
            .transaction(tx)
            .walletId(fromWalletId)
            .accountType("WALLET_" + fromWalletId)
            .entryType(EntryType.DEBIT)
            .amount(amount)
            .balanceAfter(fromWalletBalanceAfter) // ✅ Real balance after
            .build();
        ledgerEntryRepository.save(debit);
    }

    if (toWalletId != null) {
        LedgerEntry credit = LedgerEntry.builder()
            .transaction(tx)
            .walletId(toWalletId)
            .accountType("WALLET_" + toWalletId)
            .entryType(EntryType.CREDIT)
            .amount(amount)
            .balanceAfter(toWalletBalanceAfter) // ✅ Real balance after
            .build();
        ledgerEntryRepository.save(credit);
    }
}
```

### Option 2: Query Wallet Service (Not Recommended - Adds Dependency)

We could call Wallet Service to get current balance, but this:
- Adds synchronous HTTP dependency
- Creates tight coupling
- Slower (network call)

## Transaction Limit Validation Implementation

### Step 1: Create TransactionLimitService

```java
@Service
@RequiredArgsConstructor
public class TransactionLimitService {
    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletServiceClient; // HTTP client to get limits

    public void validateTransactionLimits(Long walletId, BigDecimal amount, TransactionType type) {
        // 1. Get wallet limits from Wallet Service
        WalletLimits limits = walletServiceClient.getWalletLimits(walletId);
        
        // 2. Calculate current usage
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        
        BigDecimal dailyUsage = calculateTransactionAmount(walletId, startOfDay, now, type);
        BigDecimal monthlyUsage = calculateTransactionAmount(walletId, startOfMonth, now, type);
        
        // 3. Check if new transaction would exceed limits
        if (dailyUsage.add(amount).compareTo(limits.getDailyLimit()) > 0) {
            throw new LimitExceededException("Daily limit exceeded");
        }
        
        if (monthlyUsage.add(amount).compareTo(limits.getMonthlyLimit()) > 0) {
            throw new LimitExceededException("Monthly limit exceeded");
        }
    }
    
    private BigDecimal calculateTransactionAmount(Long walletId, LocalDateTime from, LocalDateTime to, TransactionType type) {
        // Query transactions for this wallet in date range
        // Sum amounts for DEPOSIT, WITHDRAWAL, or TRANSFER (depending on type)
    }
}
```

### Step 2: Add Repository Methods

```java
// In TransactionRepository
@Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
       "WHERE (t.fromWalletId = :walletId OR t.toWalletId = :walletId) " +
       "AND t.status = 'COMPLETED' " +
       "AND t.initiatedAt >= :from " +
       "AND t.initiatedAt < :to")
BigDecimal sumTransactionAmountsByWalletAndDateRange(
    @Param("walletId") Long walletId,
    @Param("from") LocalDateTime from,
    @Param("to") LocalDateTime to
);
```

## Summary

1. **Limit Validation**: Add before transaction creation (after idempotency check)
2. **balanceAfter Fix**: Calculate from previous ledger entries using `LedgerEntryService.calculateBalanceFromLedger()`
3. **Wallet Balance Update**: Already working via Kafka event listener (asynchronous)


