package com.openwallet.wallet.repository;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.domain.WalletStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findByCustomerId(Long customerId);

    Optional<Wallet> findByCustomerIdAndId(Long customerId, Long walletId);

    List<Wallet> findByStatus(WalletStatus status);

    Optional<Wallet> findByCustomerIdAndCurrency(Long customerId, String currency);

    /**
     * Finds wallet by ID with pessimistic write lock for concurrent update protection.
     * This ensures that only one transaction can update the wallet balance at a time.
     * 
     * @param walletId Wallet ID
     * @return Wallet with lock acquired
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") Long walletId);
}
