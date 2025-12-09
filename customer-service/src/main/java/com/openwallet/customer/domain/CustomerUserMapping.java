package com.openwallet.customer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Mapping entity for fast userId (Keycloak) to customerId (internal)
 * resolution.
 * Auto-populated via JPA listeners when customers are created/updated.
 */
@Entity
@Table(name = "customer_user_mapping", indexes = {
        @Index(name = "idx_mapping_customer_id", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUserMapping {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId; // Keycloak user ID (primary key)

    @Column(name = "customer_id", nullable = false, unique = true)
    private Long customerId; // Internal customer ID

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
