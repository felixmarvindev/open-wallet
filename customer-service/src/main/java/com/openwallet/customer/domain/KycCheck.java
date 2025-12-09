package com.openwallet.customer.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * KYC (Know Your Customer) check entity representing verification records for customers.
 */
@Entity
@Table(name = "kyc_checks", indexes = {
    @Index(name = "idx_kyc_customer_id", columnList = "customer_id"),
    @Index(name = "idx_kyc_status", columnList = "status"),
    @Index(name = "idx_kyc_customer_latest", columnList = "customer_id, created_at DESC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_kyc_customer"))
    @NotNull(message = "Customer is required")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents")
    private Map<String, Object> documents; // Store document metadata as JSON

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

