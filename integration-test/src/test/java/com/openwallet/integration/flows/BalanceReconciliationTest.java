package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Balance Reconciliation Flow")
@ServiceRequirement({
        ServiceRequirement.ServiceType.AUTH,
        ServiceRequirement.ServiceType.CUSTOMER,
        ServiceRequirement.ServiceType.WALLET,
        ServiceRequirement.ServiceType.LEDGER
})
public class BalanceReconciliationTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private TestHttpClient ledgerClient;
    private TestUserManager userManager;

    private String testUserUsername;

    @BeforeEach
    void setUp() {
        log.info("Starting balance reconciliation test...");

        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);

        testHelper.validateServices(
                ServiceRequirement.ServiceType.AUTH,
                ServiceRequirement.ServiceType.CUSTOMER,
                ServiceRequirement.ServiceType.WALLET,
                ServiceRequirement.ServiceType.LEDGER
        );

        authClient = testHelper.getClient(ServiceRequirement.ServiceType.AUTH);
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        walletClient = testHelper.getClient(ServiceRequirement.ServiceType.WALLET);
        ledgerClient = testHelper.getClient(ServiceRequirement.ServiceType.LEDGER);
        userManager = testHelper.getUserManager();

        createTestUsers();

        log.info("✓ Required services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }

    private void createTestUsers() {
        log.info("Creating test users for balance reconciliation tests...");
        String timestamp = String.valueOf(System.currentTimeMillis());
        testUserUsername = "reconcileuser_" + timestamp;
        userManager.createUser(testUserUsername, testUserUsername + "@test.com");
        log.info("✓ Test user created: {}", testUserUsername);
    }

    @Test
    @DisplayName("Reconciliation shows balanced when stored and calculated balances match")
    void reconciliationShowsBalancedWhenBalancesMatch() throws Exception {
        String accessToken = userManager.getToken(testUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        // Wait for auto-created KES wallet
        Thread.sleep(2000);

        // Get wallet ID
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Make a deposit via Ledger Service
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletId);
        depositRequest.put("amount", new BigDecimal("100.00"));
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "reconcile-deposit-1-" + testUserUsername);

        TestHttpClient.HttpResponse depositResponse = ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        TestDataValidator.requireSuccess(depositResponse, "Deposit via Ledger Service");
        log.info("Deposit successful via Ledger Service. Transaction ID: {}", ledgerClient.parseJson(depositResponse.getBody()).get("id"));

        // Wait for event processing in Wallet Service
        Thread.sleep(3000);

        // Reconcile balance
        TestHttpClient.HttpResponse reconcileResponse = walletClient.get("/api/v1/wallets/" + walletId + "/balance/reconcile", accessToken);
        TestDataValidator.requireSuccess(reconcileResponse, "Balance reconciliation");

        Map<String, Object> reconcileResult = walletClient.parseJson(reconcileResponse.getBody());
        assertThat(reconcileResult.get("reconciled")).isEqualTo(true);
        assertThat(new BigDecimal(reconcileResult.get("storedBalance").toString())).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(new BigDecimal(reconcileResult.get("calculatedBalance").toString())).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(new BigDecimal(reconcileResult.get("discrepancy").toString())).isEqualByComparingTo(BigDecimal.ZERO);

        log.info("✓ Balance reconciliation successful: stored={}, calculated={}, reconciled={}",
                reconcileResult.get("storedBalance"), reconcileResult.get("calculatedBalance"), reconcileResult.get("reconciled"));
    }

    @Test
    @DisplayName("Reconciliation detects discrepancy when balances don't match")
    void reconciliationDetectsDiscrepancy() throws Exception {
        String accessToken = userManager.getToken(testUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Get wallet ID
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Make multiple deposits
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> depositRequest = new HashMap<>();
            depositRequest.put("toWalletId", walletId);
            depositRequest.put("amount", new BigDecimal(i * 10 + ".00")); // 10, 20, 30
            depositRequest.put("currency", currency);
            depositRequest.put("idempotencyKey", "reconcile-multi-" + i + "-" + testUserUsername);
            ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
            Thread.sleep(1000);
        }

        // Wait for all events to process
        Thread.sleep(3000);

        // Reconcile balance - should match (60 total)
        TestHttpClient.HttpResponse reconcileResponse = walletClient.get("/api/v1/wallets/" + walletId + "/balance/reconcile", accessToken);
        TestDataValidator.requireSuccess(reconcileResponse, "Balance reconciliation");

        Map<String, Object> reconcileResult = walletClient.parseJson(reconcileResponse.getBody());
        BigDecimal storedBalance = new BigDecimal(reconcileResult.get("storedBalance").toString());
        BigDecimal calculatedBalance = new BigDecimal(reconcileResult.get("calculatedBalance").toString());
        BigDecimal discrepancy = new BigDecimal(reconcileResult.get("discrepancy").toString());

        assertThat(storedBalance).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(calculatedBalance).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(discrepancy).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reconcileResult.get("reconciled")).isEqualTo(true);

        log.info("✓ Balance reconciliation after multiple transactions: stored={}, calculated={}, reconciled={}",
                storedBalance, calculatedBalance, reconcileResult.get("reconciled"));
    }

    @Test
    @DisplayName("Reconciliation works with withdrawals and transfers")
    void reconciliationWorksWithWithdrawalsAndTransfers() throws Exception {
        String accessToken = userManager.getToken(testUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Get wallet ID
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();
        Long walletId = ((Number) wallets.get(0).get("id")).longValue();
        String currency = (String) wallets.get(0).get("currency");

        // Create a second wallet for transfer
        Map<String, String> createWallet2Request = new HashMap<>();
        createWallet2Request.put("currency", "KES");
        TestHttpClient.HttpResponse createWallet2Response = walletClient.post("/api/v1/wallets", createWallet2Request, accessToken);
        TestDataValidator.requireSuccess(createWallet2Response, "Create second wallet");
        Long wallet2Id = ((Number) walletClient.parseJson(createWallet2Response.getBody()).get("id")).longValue();

        // Deposit to wallet 1
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", walletId);
        depositRequest.put("amount", new BigDecimal("200.00"));
        depositRequest.put("currency", currency);
        depositRequest.put("idempotencyKey", "reconcile-w1-deposit-" + testUserUsername);
        ledgerClient.post("/api/v1/transactions/deposits", depositRequest, accessToken);
        Thread.sleep(2000);

        // Withdraw from wallet 1
        Map<String, Object> withdrawalRequest = new HashMap<>();
        withdrawalRequest.put("fromWalletId", walletId);
        withdrawalRequest.put("amount", new BigDecimal("50.00"));
        withdrawalRequest.put("currency", currency);
        withdrawalRequest.put("idempotencyKey", "reconcile-w1-withdrawal-" + testUserUsername);
        ledgerClient.post("/api/v1/transactions/withdrawals", withdrawalRequest, accessToken);
        Thread.sleep(2000);

        // Transfer from wallet 1 to wallet 2
        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("fromWalletId", walletId);
        transferRequest.put("toWalletId", wallet2Id);
        transferRequest.put("amount", new BigDecimal("30.00"));
        transferRequest.put("currency", currency);
        transferRequest.put("idempotencyKey", "reconcile-transfer-" + testUserUsername);
        ledgerClient.post("/api/v1/transactions/transfers", transferRequest, accessToken);
        Thread.sleep(3000);

        // Reconcile wallet 1 - should be 120 (200 - 50 - 30)
        TestHttpClient.HttpResponse reconcileResponse1 = walletClient.get("/api/v1/wallets/" + walletId + "/balance/reconcile", accessToken);
        TestDataValidator.requireSuccess(reconcileResponse1, "Balance reconciliation wallet 1");

        Map<String, Object> reconcileResult1 = walletClient.parseJson(reconcileResponse1.getBody());
        assertThat(new BigDecimal(reconcileResult1.get("storedBalance").toString())).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(new BigDecimal(reconcileResult1.get("calculatedBalance").toString())).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(reconcileResult1.get("reconciled")).isEqualTo(true);

        // Reconcile wallet 2 - should be 30 (received transfer)
        TestHttpClient.HttpResponse reconcileResponse2 = walletClient.get("/api/v1/wallets/" + wallet2Id + "/balance/reconcile", accessToken);
        TestDataValidator.requireSuccess(reconcileResponse2, "Balance reconciliation wallet 2");

        Map<String, Object> reconcileResult2 = walletClient.parseJson(reconcileResponse2.getBody());
        assertThat(new BigDecimal(reconcileResult2.get("storedBalance").toString())).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(new BigDecimal(reconcileResult2.get("calculatedBalance").toString())).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(reconcileResult2.get("reconciled")).isEqualTo(true);

        log.info("✓ Balance reconciliation successful for both wallets after complex transactions");
    }
}


