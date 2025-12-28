package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.config.JpaConfig;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.exception.WalletAlreadyExistsException;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ JpaConfig.class, WalletService.class })
@ActiveProfiles("test")
@SuppressWarnings({ "ConstantConditions", "null" })
class WalletServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @MockBean
    private BalanceCacheService balanceCacheService;

    @Test
    void createWalletShouldSucceed() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("kes")
                .build();

        WalletResponse response = walletService.createWallet(1L, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getCurrency()).isEqualTo("KES");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createWalletShouldAllowMultipleKESWallets() {
        // Create first KES wallet
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(1L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        // Create second KES wallet - should succeed
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        WalletResponse response = walletService.createWallet(1L, request);
        
        assertThat(response.getId()).isNotNull();
        assertThat(response.getCurrency()).isEqualTo("KES");
        assertThat(response.getCustomerId()).isEqualTo(1L);
        
        // Verify both wallets exist
        List<Wallet> wallets = walletRepository.findByCustomerId(1L);
        assertThat(wallets).hasSize(2);
        assertThat(wallets).extracting(Wallet::getCurrency).containsOnly("KES");
    }

    @Test
    void createWalletShouldRejectNonKESCurrency() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("USD")
                .build();

        assertThatThrownBy(() -> walletService.createWallet(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    void createWalletShouldDefaultToKESWhenCurrencyNotProvided() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency(null) // Not provided
                .build();

        WalletResponse response = walletService.createWallet(1L, request);

        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    void getWalletShouldReturnOwnedWallet() {
        Wallet saved = Optional
                .ofNullable(walletRepository.save(Wallet.builder().customerId(2L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        WalletResponse response = walletService.getWallet(saved.getId(), 2L);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    void getWalletShouldFailWhenNotOwned() {
        Wallet saved = Optional
                .ofNullable(walletRepository.save(Wallet.builder().customerId(2L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        assertThatThrownBy(() -> walletService.getWallet(saved.getId(), 3L))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void getMyWalletsShouldReturnOnlyCustomersWallets() {
        // For MVP, only KES is supported. The unique constraint (customer_id, currency)
        // means a customer can only have one KES wallet.
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(5L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(6L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        List<WalletResponse> wallets = walletService.getMyWallets(5L);

        assertThat(wallets).hasSize(1);
        assertThat(wallets).extracting(WalletResponse::getCurrency)
                .containsExactly("KES");
    }
}
