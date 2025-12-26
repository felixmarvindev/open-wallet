package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.KafkaEventVerifier;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end balance update flow tests:
 * 1. Create user → Customer → Wallet
 * 2. Make transaction via Ledger Service
 * 3. Wait for event processing
 * 4. Verify balance updated in database, cache, and API
 * 
 * Tests the complete flow: Ledger Service → Kafka Event → Wallet Service → Database
 * 
 * Optimized: Starts AUTH, CUSTOMER, WALLET, and LEDGER services.
 * Pre-creates test users before tests run.
 */
@Slf4j
@DisplayName("Balance Update Flow")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER,
    ServiceRequirement.ServiceType.WALLET,
    ServiceRequirement.ServiceType.LEDGER
})
public class BalanceUpdateFlowTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private TestHttpClient ledgerClient;
    private TestUserManager userManager;
    private KafkaEventVerifier transactionEventsVerifier;
    
    // Store usernames for reuse across tests
    private String depositUserUsername;
    private String withdrawalUserUsername;
    private String transferUserUsername;
    private String multipleTxUserUsername;
    private String cacheUserUsername;

    @BeforeEach
    void setUp() {
        log.info("Starting balance update flow test...");
        
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
        
        // Set up Kafka event verifier
        transactionEventsVerifier = new KafkaEventVerifier(
            getInfrastructure().getKafkaBootstrapServers(), 
            "transaction-events"
        );
        
        // Create test users before tests run
        createTestUsers();
        
        log.info("✓ Required services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        if (transactionEventsVerifier != null) {
            transactionEventsVerifier.close();
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
        log.info("Creating test users for balance update tests...");
        
        // Create users with unique names to avoid conflicts
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Store usernames as instance variables
        depositUserUsername = "deposituser_" + timestamp;
        withdrawalUserUsername = "withdrawaluser_" + timestamp;
        transferUserUsername = "transferuser_" + timestamp;
        multipleTxUserUsername = "multitxuser_" + timestamp;
        cacheUserUsername = "cacheuser_" + timestamp;
        
        // Create users
        userManager.createUser(depositUserUsername, depositUserUsername + "@test.com");
        userManager.createUser(withdrawalUserUsername, withdrawalUserUsername + "@test.com");
        userManager.createUser(transferUserUsername, transferUserUsername + "@test.com");
        userManager.createUser(multipleTxUserUsername, multipleTxUserUsername + "@test.com");
        userManager.createUser(cacheUserUsername, cacheUserUsername + "@test.com");
        
        log.info("✓ Test users created: {}", timestamp);
    }

    @Test
    @DisplayName("Deposit updates wallet balance correctly")
    void depositUpdatesBalance() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, depositUserUsername);
        String accessToken = userManager.getToken(depositUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        // Step 1: Wait for wallet creation (from event)
        log.info("Step 1: Waiting for wallet creation...");
        Thread.sleep(3000);

        // Step 2: Get wallet ID
        log.info("Step 2: Getting wallet...");
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        
        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        String currency = (String) wallet.get("currency");
        BigDecimal initialBalance = new BigDecimal(wallet.get("balance").toString());
        
        log.info("Wallet found: walletId={}, currency={}, initialBalance={}", walletId, currency, initialBalance);
        assertThat(initialBalance).isEqualByComparingTo(BigDecimal.ZERO);

        // Step 3: Make deposit via Ledger Service
        log.info("Step 3: Making deposit of 100 {}...", currency);
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletId);
        depositRequest.put("amount", "100.00");
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "deposit-" + UUID.randomUUID());
        
        TestHttpClient.HttpResponse depositResponse = ledgerClient.post(
                "/api/v1/transactions/deposits",
                depositRequest,
                accessToken
        );
        TestDataValidator.requireSuccess(depositResponse, "Create deposit");
        log.info("✓ Deposit transaction created");

        // Step 4: Verify TRANSACTION_COMPLETED event published
        log.info("Step 4: Verifying TRANSACTION_COMPLETED event...");
        boolean eventFound = transactionEventsVerifier.verifyEventType("TRANSACTION_COMPLETED", 10);
        assertThat(eventFound).isTrue();
        log.info("✓ TRANSACTION_COMPLETED event verified");

        // Step 5: Wait for balance update (event processing)
        log.info("Step 5: Waiting for balance update...");
        Thread.sleep(3000);

        // Step 6: Verify balance updated via API
        log.info("Step 6: Verifying updated balance via API...");
        TestHttpClient.HttpResponse balanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        TestDataValidator.requireSuccess(balanceResponse, "Get balance");
        
        Map<String, Object> balance = walletClient.parseJson(balanceResponse.getBody());
        BigDecimal updatedBalance = new BigDecimal(balance.get("balance").toString());
        
        assertThat(updatedBalance).isEqualByComparingTo(new BigDecimal("100.00"));
        log.info("✓ Balance updated correctly: {} → {}", initialBalance, updatedBalance);
    }

    @Test
    @DisplayName("Withdrawal updates wallet balance correctly")
    void withdrawalUpdatesBalance() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, withdrawalUserUsername);
        String accessToken = userManager.getToken(withdrawalUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(3000); // Wait for wallet creation

        // Get wallet
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // First, deposit 200 to have balance
        log.info("Step 1: Depositing 200 {} to create initial balance...", currency);
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletId);
        depositRequest.put("amount", "200.00");
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "deposit-init-" + UUID.randomUUID());
        ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        Thread.sleep(3000); // Wait for deposit to process

        // Verify initial balance
        TestHttpClient.HttpResponse initialBalanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        Map<String, Object> initialBalance = walletClient.parseJson(initialBalanceResponse.getBody());
        BigDecimal initial = new BigDecimal(initialBalance.get("balance").toString());
        assertThat(initial).isEqualByComparingTo(new BigDecimal("200.00"));
        log.info("✓ Initial balance verified: {}", initial);

        // Step 2: Make withdrawal
        log.info("Step 2: Making withdrawal of 50 {}...", currency);
        Map<String, Object> withdrawalRequest = new HashMap<>();
        withdrawalRequest.put("fromWalletId", walletId);
        withdrawalRequest.put("amount", "50.00");
        withdrawalRequest.put("currency", currency);
        withdrawalRequest.put("idempotencyKey", "withdrawal-" + UUID.randomUUID());
        
        TestHttpClient.HttpResponse withdrawalResponse = ledgerClient.post(
                "/api/v1/transactions/withdrawals",
                withdrawalRequest,
                accessToken
        );
        TestDataValidator.requireSuccess(withdrawalResponse, "Create withdrawal");
        log.info("✓ Withdrawal transaction created");

        // Step 3: Wait for balance update
        Thread.sleep(3000);

        // Step 4: Verify balance updated
        log.info("Step 4: Verifying updated balance...");
        TestHttpClient.HttpResponse balanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        Map<String, Object> balance = walletClient.parseJson(balanceResponse.getBody());
        BigDecimal updated = new BigDecimal(balance.get("balance").toString());
        
        assertThat(updated).isEqualByComparingTo(new BigDecimal("150.00"));
        log.info("✓ Balance updated correctly: {} → {}", initial, updated);
    }

    @Test
    @DisplayName("Transfer updates both wallet balances correctly")
    void transferUpdatesBothWallets() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, transferUserUsername);
        String accessToken = userManager.getToken(transferUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(3000); // Wait for wallet creation

        // Get wallet (should have one auto-created)
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        Long walletAId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Create second wallet
        log.info("Step 1: Creating second wallet...");
        Map<String, String> walletRequest = new HashMap<>();
        walletRequest.put("currency", "USD");
        TestHttpClient.HttpResponse walletBResponse = walletClient.post("/api/v1/wallets", walletRequest, accessToken);
        TestDataValidator.requireSuccess(walletBResponse, "Create wallet B");
        Map<String, Object> walletB = walletClient.parseJson(walletBResponse.getBody());
        Long walletBId = ((Number) walletB.get("id")).longValue();
        log.info("✓ Second wallet created: walletBId={}", walletBId);

        // Deposit to wallet A
        log.info("Step 2: Depositing 100 {} to wallet A...", currency);
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletAId);
        depositRequest.put("amount", "100.00");
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "deposit-walletA-" + UUID.randomUUID());
        ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        Thread.sleep(3000);

        // Deposit to wallet B
        log.info("Step 3: Depositing 50 {} to wallet B...", currency);
        depositRequest.put("toWalletId", walletBId);
        depositRequest.put("amount", "50.00");
        depositRequest.put("idempotencyKey", "deposit-walletB-" + UUID.randomUUID());
        ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        Thread.sleep(3000);

        // Verify initial balances
        TestHttpClient.HttpResponse balanceAResponse = walletClient.get(
                "/api/v1/wallets/" + walletAId + "/balance",
                accessToken
        );
        TestHttpClient.HttpResponse balanceBResponse = walletClient.get(
                "/api/v1/wallets/" + walletBId + "/balance",
                accessToken
        );
        BigDecimal balanceA = new BigDecimal(walletClient.parseJson(balanceAResponse.getBody()).get("balance").toString());
        BigDecimal balanceB = new BigDecimal(walletClient.parseJson(balanceBResponse.getBody()).get("balance").toString());
        assertThat(balanceA).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(balanceB).isEqualByComparingTo(new BigDecimal("50.00"));
        log.info("✓ Initial balances: Wallet A={}, Wallet B={}", balanceA, balanceB);

        // Step 4: Transfer 30 from A to B
        log.info("Step 4: Transferring 30 {} from wallet A to wallet B...", currency);
        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("fromWalletId", walletAId);
        transferRequest.put("toWalletId", walletBId);
        transferRequest.put("amount", "30.00");
        transferRequest.put("currency", currency);
        transferRequest.put("idempotencyKey", "transfer-" + UUID.randomUUID());
        
        TestHttpClient.HttpResponse transferResponse = ledgerClient.post(
                "/api/v1/transactions/transfers",
                transferRequest,
                accessToken
        );
        TestDataValidator.requireSuccess(transferResponse, "Create transfer");
        log.info("✓ Transfer transaction created");

        // Step 5: Wait for balance updates
        Thread.sleep(3000);

        // Step 6: Verify both balances updated
        log.info("Step 6: Verifying updated balances...");
        balanceAResponse = walletClient.get("/api/v1/wallets/" + walletAId + "/balance", accessToken);
        balanceBResponse = walletClient.get("/api/v1/wallets/" + walletBId + "/balance", accessToken);
        
        BigDecimal updatedBalanceA = new BigDecimal(walletClient.parseJson(balanceAResponse.getBody()).get("balance").toString());
        BigDecimal updatedBalanceB = new BigDecimal(walletClient.parseJson(balanceBResponse.getBody()).get("balance").toString());
        
        assertThat(updatedBalanceA).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(updatedBalanceB).isEqualByComparingTo(new BigDecimal("80.00"));
        log.info("✓ Balances updated correctly:");
        log.info("  Wallet A: {} → {}", balanceA, updatedBalanceA);
        log.info("  Wallet B: {} → {}", balanceB, updatedBalanceB);
    }

    @Test
    @DisplayName("Multiple transactions update balance correctly")
    void multipleTransactionsUpdateBalance() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, multipleTxUserUsername);
        String accessToken = userManager.getToken(multipleTxUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(3000); // Wait for wallet creation

        // Get wallet
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Make 3 deposits: 10, 20, 30
        log.info("Step 1: Making multiple deposits...");
        String[] amounts = {"10.00", "20.00", "30.00"};
        for (String amount : amounts) {
            Map<String, Object> depositRequest = new HashMap<>();
            depositRequest.put("toWalletId", walletId);
            depositRequest.put("amount", amount);
            depositRequest.put("currency", currency);
            depositRequest.put("idempotencyKey", "deposit-" + amount + "-" + UUID.randomUUID());
            
            ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
            Thread.sleep(2000); // Wait between transactions
        }
        log.info("✓ All deposits created");

        // Wait for final processing
        Thread.sleep(3000);

        // Verify final balance
        log.info("Step 2: Verifying final balance...");
        TestHttpClient.HttpResponse balanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        Map<String, Object> balance = walletClient.parseJson(balanceResponse.getBody());
        BigDecimal finalBalance = new BigDecimal(balance.get("balance").toString());
        
        assertThat(finalBalance).isEqualByComparingTo(new BigDecimal("60.00"));
        log.info("✓ Final balance correct: {}", finalBalance);
    }

    @Test
    @DisplayName("Balance is consistent across API and cache")
    void balanceConsistencyAfterTransaction() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, cacheUserUsername);
        String accessToken = userManager.getToken(cacheUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(3000); // Wait for wallet creation

        // Get wallet
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Get initial balance
        TestHttpClient.HttpResponse initialBalanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        BigDecimal initialBalance = new BigDecimal(
                walletClient.parseJson(initialBalanceResponse.getBody()).get("balance").toString()
        );
        log.info("Initial balance: {}", initialBalance);

        // Make deposit
        log.info("Step 1: Making deposit...");
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletId);
        depositRequest.put("amount", "75.50");
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "deposit-consistency-" + UUID.randomUUID());
        ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        log.info("✓ Deposit created");

        // Wait for processing
        Thread.sleep(3000);

        // Verify balance via API (should reflect cache and database)
        log.info("Step 2: Verifying balance consistency...");
        TestHttpClient.HttpResponse balanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        Map<String, Object> balance = walletClient.parseJson(balanceResponse.getBody());
        BigDecimal updatedBalance = new BigDecimal(balance.get("balance").toString());
        
        assertThat(updatedBalance).isEqualByComparingTo(new BigDecimal("75.50"));
        
        // Verify wallet endpoint also shows correct balance
        TestHttpClient.HttpResponse walletResponse = walletClient.get(
                "/api/v1/wallets/" + walletId,
                accessToken
        );
        Map<String, Object> wallet = walletClient.parseJson(walletResponse.getBody());
        BigDecimal walletBalance = new BigDecimal(wallet.get("balance").toString());
        
        assertThat(walletBalance).isEqualByComparingTo(updatedBalance);
        log.info("✓ Balance consistent: API={}, Wallet={}", updatedBalance, walletBalance);
    }
}

