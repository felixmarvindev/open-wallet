package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceCacheTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private BalanceCacheService balanceCacheService;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getWalletCachesBalance() {
        Wallet wallet = Wallet.builder()
                .id(1L)
                .customerId(10L)
                .currency("KES")
                .balance(BigDecimal.TEN)
                .build();
        when(walletRepository.findByCustomerIdAndId(10L, 1L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWallet(1L, 10L);

        assertThat(response.getId()).isEqualTo(1L);
        ArgumentCaptor<BalanceCacheService.BalanceSnapshot> captor = ArgumentCaptor
                .forClass(BalanceCacheService.BalanceSnapshot.class);
        verify(balanceCacheService).putBalance(org.mockito.Mockito.eq(1L), captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualTo("10");
    }
}
