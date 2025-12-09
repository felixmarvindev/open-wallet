package com.openwallet.wallet.service;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.exception.WalletAlreadyExistsException;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public WalletResponse createWallet(Long customerId, CreateWalletRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request == null || !StringUtils.hasText(request.getCurrency())) {
            throw new IllegalArgumentException("Currency is required");
        }

        String currency = request.getCurrency().toUpperCase(Locale.ROOT);
        walletRepository.findByCustomerIdAndCurrency(customerId, currency).ifPresent(existing -> {
            throw new WalletAlreadyExistsException("Wallet already exists for currency: " + currency);
        });

        Wallet wallet = Wallet.builder()
                .customerId(customerId)
                .currency(currency)
                .dailyLimit(defaultLimit(request.getDailyLimit(), new BigDecimal("100000.00")))
                .monthlyLimit(defaultLimit(request.getMonthlyLimit(), new BigDecimal("1000000.00")))
                .build();

        Wallet saved = walletRepository.save(wallet);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId, Long customerId) {
        return walletRepository.findByCustomerIdAndId(customerId, walletId)
                .map(this::toResponse)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getMyWallets(Long customerId) {
        return walletRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .customerId(wallet.getCustomerId())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .balance(wallet.getBalance())
                .dailyLimit(wallet.getDailyLimit())
                .monthlyLimit(wallet.getMonthlyLimit())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private BigDecimal defaultLimit(BigDecimal provided, BigDecimal fallback) {
        return provided != null ? provided : fallback;
    }
}


