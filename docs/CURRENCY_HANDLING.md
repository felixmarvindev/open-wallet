# Currency Handling Strategy

## Current State

### What We Have
1. **Wallet** has `currency` field (3-character ISO code, default "KES")
2. **Transaction** has `currency` field (3-character ISO code, default "KES")
3. **LedgerEntry** does NOT have `currency` field ❌
4. **Unique constraint**: `(customer_id, currency)` - customers can have multiple wallets with different currencies
5. **Currency validation**: ISO 4217 validator exists but not enforced in transaction validation

### Current Problems

#### 1. **No Currency Validation**
- Transaction currency is not validated against wallet currency
- A USD transaction could be applied to a KES wallet
- Comment in code: "Currency validation between wallets would require wallet service integration"

#### 2. **LedgerEntry Missing Currency**
- `LedgerEntry` doesn't track currency
- `balanceAfter` calculation mixes amounts from different currencies
- Example: If wallet has 100 USD and 50 EUR entries, balance calculation would incorrectly sum them

#### 3. **Cross-Currency Transfers**
- No validation that transfer wallets have matching currencies
- No currency conversion logic
- Transfer from USD wallet to EUR wallet would fail silently or cause data corruption

#### 4. **Balance Calculation Issues**
- `calculateBalanceFromLedger()` sums all entries regardless of currency
- Should only sum entries in the same currency as the wallet

## Solution Options

### Option 1: Same-Currency Only (Recommended for MVP)

**Strategy**: Enforce that all transactions must match wallet currency. Reject cross-currency operations.

**Implementation**:
1. Add currency validation in `TransactionService`
2. Validate transaction currency matches wallet currency before processing
3. Reject transfers between different currency wallets
4. Add currency field to `LedgerEntry` for audit trail
5. Update balance calculation to filter by currency

**Pros**:
- Simple to implement
- No currency conversion complexity
- Clear business rules
- Prevents data corruption

**Cons**:
- Users can't transfer between different currency wallets
- Requires users to have separate wallets for each currency

### Option 2: Currency Conversion (Future Enhancement)

**Strategy**: Allow cross-currency transfers with automatic conversion using exchange rates.

**Implementation**:
1. Add exchange rate service (external API or internal rates)
2. Convert amounts during transfer
3. Track original and converted amounts
4. Handle conversion fees

**Pros**:
- Better user experience
- Supports international use cases

**Cons**:
- Complex implementation
- Requires exchange rate service
- Conversion fees and rounding issues
- Rate volatility

## Recommended Implementation: Option 1

### Step 1: Add Currency to LedgerEntry

```sql
-- Migration: Add currency to ledger_entries
ALTER TABLE ledger_entries ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KES';
CREATE INDEX idx_ledger_entries_currency ON ledger_entries(currency);
```

```java
// LedgerEntry.java
@Column(name = "currency", nullable = false, length = 3)
@NotBlank(message = "Currency is required")
@Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO code)")
private String currency;
```

### Step 2: Create Wallet Service Client for Currency Validation

```java
// In ledger-service
@Service
@RequiredArgsConstructor
public class WalletServiceClient {
    private final WebClient.Builder webClientBuilder;
    @Value("${app.services.wallet.base-url}")
    private String walletServiceBaseUrl;

    public WalletInfo getWalletInfo(Long walletId, String accessToken) {
        // HTTP call to wallet service to get wallet currency
        // Returns: { id, currency, status, ... }
    }
}
```

### Step 3: Add Currency Validation to TransactionService

```java
private void validateCurrencyMatchesWallet(Long walletId, String transactionCurrency, String operation) {
    if (walletId == null) {
        return; // Cash account, no validation needed
    }
    
    WalletInfo wallet = walletServiceClient.getWalletInfo(walletId, getAccessToken());
    if (!wallet.getCurrency().equals(transactionCurrency)) {
        throw new CurrencyMismatchException(
            String.format("Transaction currency %s does not match wallet %d currency %s", 
                transactionCurrency, walletId, wallet.getCurrency())
        );
    }
}

private void validateTransferRequest(TransferRequest request) {
    // ... existing validations ...
    
    // Validate currencies match
    WalletInfo fromWallet = walletServiceClient.getWalletInfo(request.getFromWalletId(), getAccessToken());
    WalletInfo toWallet = walletServiceClient.getWalletInfo(request.getToWalletId(), getAccessToken());
    
    if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
        throw new CurrencyMismatchException(
            String.format("Cannot transfer between different currencies: %s -> %s", 
                fromWallet.getCurrency(), toWallet.getCurrency())
        );
    }
    
    // Validate transaction currency matches both wallets
    if (!request.getCurrency().equals(fromWallet.getCurrency())) {
        throw new CurrencyMismatchException(
            String.format("Transaction currency %s does not match wallet currency %s", 
                request.getCurrency(), fromWallet.getCurrency())
        );
    }
}
```

### Step 4: Update createDoubleEntry to Include Currency

```java
private void createDoubleEntry(Transaction tx, Long fromWalletId, Long toWalletId, BigDecimal amount) {
    String currency = tx.getCurrency(); // Get currency from transaction
    
    // Calculate balances - only for entries in the same currency
    BigDecimal fromWalletBalanceBefore = BigDecimal.ZERO;
    if (fromWalletId != null) {
        fromWalletBalanceBefore = ledgerEntryService.calculateBalanceFromLedger(
            fromWalletId, currency); // Filter by currency
    }
    
    // ... create entries with currency field ...
    LedgerEntry debit = LedgerEntry.builder()
        .transaction(tx)
        .walletId(fromWalletId)
        .currency(currency) // ✅ Add currency
        .entryType(EntryType.DEBIT)
        .amount(amount)
        .balanceAfter(debitBalanceAfter)
        .build();
}
```

### Step 5: Update Balance Calculation to Filter by Currency

```java
// LedgerEntryService.java
@Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) " +
       "FROM LedgerEntry le " +
       "WHERE le.walletId = :walletId AND le.currency = :currency")
BigDecimal calculateBalanceFromLedger(
    @Param("walletId") Long walletId,
    @Param("currency") String currency
);
```

### Step 6: Create CurrencyMismatchException

```java
public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
```

## Alternative: Direct Database Access (If Shared DB)

If both services share the same database, we can query wallets table directly:

```java
// In ledger-service, read-only access to wallets table
@Entity
@Immutable
@Table(name = "wallets")
public class Wallet {
    @Id
    private Long id;
    @Column(name = "currency")
    private String currency;
}

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    // Read-only access
}
```

This avoids HTTP calls but creates tighter coupling.

## Migration Plan

1. **Phase 1**: Add currency field to LedgerEntry (database migration)
2. **Phase 2**: Update createDoubleEntry to set currency
3. **Phase 3**: Add currency validation (wallet service client or direct DB access)
4. **Phase 4**: Update balance calculation to filter by currency
5. **Phase 5**: Add tests for currency validation

## Testing Scenarios

1. ✅ Deposit with matching currency
2. ❌ Deposit with mismatched currency (should reject)
3. ✅ Transfer between same-currency wallets
4. ❌ Transfer between different-currency wallets (should reject)
5. ✅ Balance calculation only includes same-currency entries
6. ✅ Multiple wallets with different currencies for same customer

