package com.openwallet.wallet.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wallet entity representing a customer's wallet with balance and transaction limits.
 * Note: customer_id references customers table but is stored as BIGINT (cross-service reference).
 */
@Entity
@Table(name = "wallets", 
    indexes = {
        @Index(name = "idx_wallets_customer_id", columnList = "customer_id"),
        @Index(name = "idx_wallets_status", columnList = "status"),
        @Index(name = "idx_wallets_currency", columnList = "currency")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    @NotNull(message = "Customer ID is required")
    private Long customerId; // References customers table (cross-service reference)

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO code)")
    @Builder.Default
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Balance is required")
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Daily limit is required")
    @DecimalMin(value = "0.00", message = "Daily limit cannot be negative")
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("100000.00");

    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Monthly limit is required")
    @DecimalMin(value = "0.00", message = "Monthly limit cannot be negative")
    @Builder.Default
    private BigDecimal monthlyLimit = new BigDecimal("1000000.00");

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

