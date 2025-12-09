package com.openwallet.wallet.repository;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.domain.WalletStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findByCustomerId(Long customerId);

    Optional<Wallet> findByCustomerIdAndId(Long customerId, Long walletId);

    List<Wallet> findByStatus(WalletStatus status);

    Optional<Wallet> findByCustomerIdAndCurrency(Long customerId, String currency);
}
