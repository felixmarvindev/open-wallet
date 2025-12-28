package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.InfrastructureManager;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.OptimizedTestHelper;
import com.openwallet.integration.utils.TestDataValidator;
import com.openwallet.integration.utils.TestHttpClient;
import com.openwallet.integration.utils.TestUserManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transaction limit validation:
 * 1. Transactions are rejected when daily limit is exceeded
 * 2. Transactions are rejected when monthly limit is exceeded
 * 3. Transactions succeed when within limits
 * 4. Limit validation works across services (wallet-service ↔ ledger-service)
 * 5. Multiple transactions within limits are allowed
 * 
 * Flow:
 * 1. Create user → Customer → Wallet (with specific limits)
 * 2. Make transactions via Ledger Service
 * 3. Verify limit validation works correctly
 * 
 * Optimized: Starts AUTH, CUSTOMER, WALLET, and LEDGER services.
 * Pre-creates test users before tests run.
 */
@Slf4j
@DisplayName("Transaction Limit Validation")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER,
    ServiceRequirement.ServiceType.WALLET,
    ServiceRequirement.ServiceType.LEDGER
})
public class TransactionLimitValidationTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private TestHttpClient ledgerClient;
    private TestUserManager userManager;
    
    // Database connection for updating transaction timestamps
    private Connection dbConnection;
    private int currentDayOffset = 0; // Track which day we're simulating
    
    // Store usernames for reuse across tests
    private String dailyLimitUserUsername;
    private String monthlyLimitUserUsername;
    private String withinLimitUserUsername;
    private String multipleTxUserUsername;
    private String walletNotFoundUserUsername;

    @BeforeEach
    void setUp() {
        log.info("Starting transaction limit validation test...");
        
        // Initialize helper (auto-starts only required services)
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);
        
        // Validate services are running (fast-fail)
        testHelper.validateServices(
            ServiceRequirement.ServiceType.AUTH,
            ServiceRequirement.ServiceType.CUSTOMER,
            ServiceRequirement.ServiceType.WALLET,
            ServiceRequirement.ServiceType.LEDGER
        );
        
        // Get clients
        authClient = testHelper.getClient(ServiceRequirement.ServiceType.AUTH);
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        walletClient = testHelper.getClient(ServiceRequirement.ServiceType.WALLET);
        ledgerClient = testHelper.getClient(ServiceRequirement.ServiceType.LEDGER);
        userManager = testHelper.getUserManager();
        
        // Initialize database connection for timestamp manipulation
        initializeDatabaseConnection();
        
        // Create test users before tests run
        createTestUsers();
        
        log.info("✓ Required services started and ready for testing");
    }
    
    /**
     * Initializes database connection for updating transaction timestamps.
     */
    private void initializeDatabaseConnection() {
        try {
            InfrastructureManager infrastructure = getInfrastructure();
            String jdbcUrl = infrastructure.getPostgresJdbcUrl();
            String username = infrastructure.getPostgresUsername();
            String password = infrastructure.getPostgresPassword();
            
            dbConnection = DriverManager.getConnection(jdbcUrl, username, password);
            log.info("✓ Database connection established for timestamp manipulation");
        } catch (Exception e) {
            log.error("Failed to establish database connection: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to connect to database", e);
        }
    }
    
    /**
     * Updates the initiated_at timestamp of the most recent transaction for a wallet
     * to simulate time passing (moving transaction to N days ago).
     * This allows testing monthly limits without hitting daily limits.
     * 
     * @param walletId Wallet ID
     * @param daysAgo Number of days ago to set the transaction timestamp
     */
    private void updateTransactionTimestampToDaysAgo(Long walletId, int daysAgo) {
        try {
            // Update the most recent transaction for this wallet to be N days ago
            String updateSql = "UPDATE transactions " +
                    "SET initiated_at = CURRENT_TIMESTAMP - INTERVAL '" + daysAgo + " days' " +
                    "WHERE id = (SELECT id FROM transactions " +
                    "           WHERE (from_wallet_id = ? OR to_wallet_id = ?) " +
                    "           ORDER BY initiated_at DESC LIMIT 1)";
            
            try (PreparedStatement stmt = dbConnection.prepareStatement(updateSql)) {
                stmt.setLong(1, walletId);
                stmt.setLong(2, walletId);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    log.debug("✓ Updated transaction timestamp for walletId={} to {} days ago", walletId, daysAgo);
                } else {
                    log.warn("No transaction found to update timestamp for walletId={}", walletId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update transaction timestamp: {}", e.getMessage(), e);
            // Don't throw - this is a test helper, continue with test
        }
    }

    @AfterEach
    void tearDown() {
        // Reset day offset for next test
        currentDayOffset = 0;
        
        // Close database connection
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (Exception e) {
                log.warn("Failed to close database connection: {}", e.getMessage());
            }
        }
        
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }

    /**
     * Creates test users that will be used by tests.
     * Users are created once before tests run, not during test execution.
     */
    private void createTestUsers() {
        log.info("Creating test users...");
        
        dailyLimitUserUsername = "dailylimit_" + System.currentTimeMillis();
        monthlyLimitUserUsername = "monthlylimit_" + System.currentTimeMillis();
        withinLimitUserUsername = "withinlimit_" + System.currentTimeMillis();
        multipleTxUserUsername = "multitx_" + System.currentTimeMillis();
        walletNotFoundUserUsername = "walletnotfound_" + System.currentTimeMillis();
        
        userManager.createUser(dailyLimitUserUsername, dailyLimitUserUsername + "@example.com");
        userManager.createUser(monthlyLimitUserUsername, monthlyLimitUserUsername + "@example.com");
        userManager.createUser(withinLimitUserUsername, withinLimitUserUsername + "@example.com");
        userManager.createUser(multipleTxUserUsername, multipleTxUserUsername + "@example.com");
        userManager.createUser(walletNotFoundUserUsername, walletNotFoundUserUsername + "@example.com");
        
        log.info("✓ Test users created");
    }

    @Test
    @DisplayName("Deposit should be rejected when daily limit is exceeded")
    void depositShouldBeRejectedWhenDailyLimitExceeded() throws Exception {
        log.info("Test: Deposit rejected when daily limit exceeded");
        
        // Step 1: Wait for wallet creation (from event) and get wallet
        Thread.sleep(3000); // Wait for wallet creation event to process
        String accessToken = userManager.getToken(dailyLimitUserUsername);
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal dailyLimit = new BigDecimal(wallet.get("dailyLimit").toString());
        
        log.info("Wallet ID: {}, Daily Limit: {}", walletId, dailyLimit);
        
        // Step 2: Make first deposit that uses most of the limit
        BigDecimal firstAmount = dailyLimit.multiply(new BigDecimal("0.9")); // 90% of limit
        Map<String, Object> firstDeposit = new HashMap<>();
        firstDeposit.put("toWalletId", walletId);
        firstDeposit.put("amount", firstAmount);
        firstDeposit.put("currency", "KES");
        firstDeposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse firstResponse = ledgerClient.post(
            "/api/v1/transactions/deposits",
            firstDeposit,
            accessToken
        );
        TestDataValidator.requireSuccess(firstResponse, "First deposit");
        log.info("✓ First deposit successful: {}", firstAmount);
        
        // Step 3: Try to make second deposit that would exceed daily limit
        BigDecimal secondAmount = dailyLimit.multiply(new BigDecimal("0.2")); // 20% of limit
        // Total would be 90% + 20% = 110% > 100%
        Map<String, Object> secondDeposit = new HashMap<>();
        secondDeposit.put("toWalletId", walletId);
        secondDeposit.put("amount", secondAmount);
        secondDeposit.put("currency", "KES");
        secondDeposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse secondResponse = ledgerClient.post(
            "/api/v1/transactions/deposits",
            secondDeposit,
            accessToken
        );
        
        // Step 4: Verify second deposit was rejected
        assertThat(secondResponse.getStatusCode()).isEqualTo(400);
        log.info("✓ Second deposit correctly rejected with status 400");
        
        Map<String, Object> errorBody = ledgerClient.parseJson(secondResponse.getBody());
        String errorMessage = (String) errorBody.get("message");
        assertThat(errorMessage).containsIgnoringCase("limit");
        assertThat(errorMessage).containsIgnoringCase("daily");
        log.info("✓ Error message contains limit information: {}", errorMessage);
    }

    @Test
    @DisplayName("Deposit should be rejected when monthly limit is exceeded")
    void depositShouldBeRejectedWhenMonthlyLimitExceeded() throws Exception {
        log.info("Test: Deposit rejected when monthly limit exceeded");
        
        // Step 1: Wait for wallet creation (from event) and get wallet
        Thread.sleep(3000); // Wait for wallet creation event to process
        String accessToken = userManager.getToken(monthlyLimitUserUsername);
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal monthlyLimit = new BigDecimal(wallet.get("monthlyLimit").toString());
        BigDecimal dailyLimit = new BigDecimal(wallet.get("dailyLimit").toString());
        
        log.info("Wallet ID: {}, Daily Limit: {}, Monthly Limit: {}", walletId, dailyLimit, monthlyLimit);
        
        // Strategy: Make multiple deposits, each on a different day
        // This way daily limit resets each day but monthly limit accumulates
        // Then make one more deposit that would exceed monthly limit
        
        // Step 2: Make deposits over multiple days to reach 95% of monthly limit
        // Each deposit stays within daily limit, but we update timestamps to simulate different days
        BigDecimal targetMonthlyUsage = monthlyLimit.multiply(new BigDecimal("0.95"));
        BigDecimal maxPerDay = dailyLimit.multiply(new BigDecimal("0.99")); // Stay just under daily limit
        
        BigDecimal totalDeposited = BigDecimal.ZERO;
        int depositCount = 0;
        int currentDay = 1; // Start with 1 day ago
        
        while (totalDeposited.compareTo(targetMonthlyUsage) < 0) {
            BigDecimal remainingToTarget = targetMonthlyUsage.subtract(totalDeposited);
            BigDecimal depositAmount = remainingToTarget.min(maxPerDay);
            
            // If remaining amount is very small, use it all
            if (depositAmount.compareTo(new BigDecimal("0.01")) < 0) {
                break;
            }
            
            Map<String, Object> deposit = new HashMap<>();
            deposit.put("toWalletId", walletId);
            deposit.put("amount", depositAmount);
            deposit.put("currency", "KES");
            deposit.put("idempotencyKey", UUID.randomUUID().toString());
            
            TestHttpClient.HttpResponse response = ledgerClient.post(
                "/api/v1/transactions/deposits",
                deposit,
                accessToken
            );
            TestDataValidator.requireSuccess(response, "Deposit " + (depositCount + 1));
            
            // Update this transaction's timestamp to be N days ago (simulates time passing)
            // This resets daily limit for next transaction but keeps monthly limit accumulation
            updateTransactionTimestampToDaysAgo(walletId, currentDay);
            log.info("✓ Deposit {} successful: {} (moved to {} days ago, total: {})", 
                    depositCount + 1, depositAmount, currentDay, totalDeposited.add(depositAmount));
            
            totalDeposited = totalDeposited.add(depositAmount);
            depositCount++;
            currentDay++; // Next deposit will be on next day
            
            // Small delay to ensure transactions are processed
            Thread.sleep(500);
        }
        
        log.info("✓ Made {} deposits over {} days totaling {} ({}% of monthly limit)", 
                depositCount, currentDay - 1, totalDeposited, 
                totalDeposited.divide(monthlyLimit, 2, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
        
        // Step 3: Try to make one more deposit that would exceed monthly limit
        // This deposit will be on "today" (current day), so daily limit applies but monthly limit is already at 95%
        BigDecimal secondAmount = monthlyLimit.multiply(new BigDecimal("0.1")); // 10% of limit
        // But ensure it doesn't exceed daily limit
        if (secondAmount.compareTo(dailyLimit) > 0) {
            secondAmount = dailyLimit.multiply(new BigDecimal("0.9")); // Use 90% of daily limit
        }
        // Total would be 95% + 10% = 105% > 100% (monthly limit exceeded)
        Map<String, Object> secondDeposit = new HashMap<>();
        secondDeposit.put("toWalletId", walletId);
        secondDeposit.put("amount", secondAmount);
        secondDeposit.put("currency", "KES");
        secondDeposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse secondResponse = ledgerClient.post(
            "/api/v1/transactions/deposits",
            secondDeposit,
            accessToken
        );
        
        // Step 4: Verify second deposit was rejected
        assertThat(secondResponse.getStatusCode()).isEqualTo(400);
        log.info("✓ Final deposit correctly rejected with status 400");
        
        Map<String, Object> errorBody = ledgerClient.parseJson(secondResponse.getBody());
        String errorMessage = (String) errorBody.get("message");
        assertThat(errorMessage).containsIgnoringCase("limit");
        assertThat(errorMessage).containsIgnoringCase("monthly");
        log.info("✓ Error message contains limit information: {}", errorMessage);
    }

    @Test
    @DisplayName("Deposit should succeed when within limits")
    void depositShouldSucceedWhenWithinLimits() throws Exception {
        log.info("Test: Deposit succeeds when within limits");
        
        // Step 1: Wait for wallet creation (from event) and get wallet
        Thread.sleep(3000); // Wait for wallet creation event to process
        String accessToken = userManager.getToken(withinLimitUserUsername);
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal dailyLimit = new BigDecimal(wallet.get("dailyLimit").toString());
        
        log.info("Wallet ID: {}, Daily Limit: {}", walletId, dailyLimit);
        
        // Step 2: Make deposit that is well within the limit (50% of limit)
        BigDecimal depositAmount = dailyLimit.multiply(new BigDecimal("0.5"));
        Map<String, Object> deposit = new HashMap<>();
        deposit.put("toWalletId", walletId);
        deposit.put("amount", depositAmount);
        deposit.put("currency", "KES");
        deposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse response = ledgerClient.post(
            "/api/v1/transactions/deposits",
            deposit,
            accessToken
        );
        
        // Step 3: Verify deposit was successful
        TestDataValidator.requireSuccess(response, "Deposit within limits");
        log.info("✓ Deposit successful: {}", depositAmount);
        
        Map<String, Object> transaction = ledgerClient.parseJson(response.getBody());
        assertThat(transaction.get("status")).isEqualTo("COMPLETED");
        assertThat(new BigDecimal(transaction.get("amount").toString())).isEqualByComparingTo(depositAmount);
    }

    @Test
    @DisplayName("Multiple transactions within limits should be allowed")
    void multipleTransactionsWithinLimitsShouldBeAllowed() throws Exception {
        log.info("Test: Multiple transactions within limits");
        
        // Step 1: Wait for wallet creation (from event) and get wallet
        Thread.sleep(3000); // Wait for wallet creation event to process
        String accessToken = userManager.getToken(multipleTxUserUsername);
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal dailyLimit = new BigDecimal(wallet.get("dailyLimit").toString());
        
        log.info("Wallet ID: {}, Daily Limit: {}", walletId, dailyLimit);
        
        // Step 2: Make multiple deposits, each 10% of limit (total will be 30% < 100%)
        int numberOfTransactions = 3;
        BigDecimal amountPerTransaction = dailyLimit.multiply(new BigDecimal("0.1"));
        
        for (int i = 0; i < numberOfTransactions; i++) {
            Map<String, Object> deposit = new HashMap<>();
            deposit.put("toWalletId", walletId);
            deposit.put("amount", amountPerTransaction);
            deposit.put("currency", "KES");
            deposit.put("idempotencyKey", UUID.randomUUID().toString());
            
            TestHttpClient.HttpResponse response = ledgerClient.post(
                "/api/v1/transactions/deposits",
                deposit,
                accessToken
            );
            
            TestDataValidator.requireSuccess(response, "Deposit " + (i + 1));
            log.info("✓ Deposit {} successful: {}", i + 1, amountPerTransaction);
        }
        
        log.info("✓ All {} transactions completed successfully", numberOfTransactions);
    }

    @Test
    @DisplayName("Transaction should be rejected when wallet not found")
    void transactionShouldBeRejectedWhenWalletNotFound() throws Exception {
        log.info("Test: Transaction rejected when wallet not found");
        
        // Step 1: Get user (but don't use their wallet)
        String accessToken = userManager.getToken(walletNotFoundUserUsername);
        
        // Step 2: Try to make deposit to non-existent wallet
        Map<String, Object> deposit = new HashMap<>();
        deposit.put("toWalletId", 99999L); // Non-existent wallet ID
        deposit.put("amount", new BigDecimal("100.00"));
        deposit.put("currency", "KES");
        deposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse response = ledgerClient.post(
            "/api/v1/transactions/deposits",
            deposit,
            accessToken
        );
        
        // Step 3: Verify transaction was rejected
        assertThat(response.getStatusCode()).isEqualTo(404);
        log.info("✓ Transaction correctly rejected with status 404");
        
        Map<String, Object> errorBody = ledgerClient.parseJson(response.getBody());
        String errorMessage = (String) errorBody.get("message");
        assertThat(errorMessage).containsIgnoringCase("wallet");
        assertThat(errorMessage).containsIgnoringCase("not found");
        log.info("✓ Error message indicates wallet not found: {}", errorMessage);
    }

    @Test
    @DisplayName("Withdrawal should be rejected when daily limit is exceeded")
    void withdrawalShouldBeRejectedWhenDailyLimitExceeded() throws Exception {
        log.info("Test: Withdrawal rejected when daily limit exceeded");
        
        // Step 1: Wait for wallet creation (from event) and get wallet
        Thread.sleep(3000); // Wait for wallet creation event to process
        String accessToken = userManager.getToken(dailyLimitUserUsername);
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal dailyLimit = new BigDecimal(wallet.get("dailyLimit").toString());
        
        // First, deposit some money to have balance for withdrawal
        BigDecimal depositAmount = dailyLimit.multiply(new BigDecimal("0.9"));
        dailyLimit = dailyLimit.subtract(depositAmount);
        Map<String, Object> deposit = new HashMap<>();
        deposit.put("toWalletId", walletId);
        deposit.put("amount", depositAmount);
        deposit.put("currency", "KES");
        deposit.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse depositResponse = ledgerClient.post(
            "/api/v1/transactions/deposits",
            deposit,
            accessToken
        );
        TestDataValidator.requireSuccess(depositResponse, "Initial deposit");
        log.info("✓ Initial deposit successful: {}", depositAmount);
        
        // Wait a bit for balance to update
        Thread.sleep(1000);
        
        // Step 2: Make first withdrawal that uses most of the limit
        BigDecimal firstAmount = dailyLimit.multiply(new BigDecimal("0.9"));
        Map<String, Object> firstWithdrawal = new HashMap<>();
        firstWithdrawal.put("fromWalletId", walletId);
        firstWithdrawal.put("amount", firstAmount);
        firstWithdrawal.put("currency", "KES");
        firstWithdrawal.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse firstResponse = ledgerClient.post(
            "/api/v1/transactions/withdrawals",
            firstWithdrawal,
            accessToken
        );
        TestDataValidator.requireSuccess(firstResponse, "First withdrawal");
        log.info("✓ First withdrawal successful: {}", firstAmount);
        
        // Step 3: Try to make second withdrawal that would exceed daily limit
        BigDecimal secondAmount = dailyLimit.multiply(new BigDecimal("0.2"));
        Map<String, Object> secondWithdrawal = new HashMap<>();
        secondWithdrawal.put("fromWalletId", walletId);
        secondWithdrawal.put("amount", secondAmount);
        secondWithdrawal.put("currency", "KES");
        secondWithdrawal.put("idempotencyKey", UUID.randomUUID().toString());
        
        TestHttpClient.HttpResponse secondResponse = ledgerClient.post(
            "/api/v1/transactions/withdrawals",
            secondWithdrawal,
            accessToken
        );
        
        // Step 4: Verify second withdrawal was rejected
        assertThat(secondResponse.getStatusCode()).isEqualTo(400);
        log.info("✓ Second withdrawal correctly rejected with status 400");
    }
}

