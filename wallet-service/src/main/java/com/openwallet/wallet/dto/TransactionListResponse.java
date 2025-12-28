package com.openwallet.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response DTO for paginated transaction list queries.
 * Matches the structure from Ledger Service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionListResponse {
    private List<TransactionItem> transactions;
    private PaginationMetadata pagination;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionItem {
        private Long id;
        private String transactionType;
        private String status;
        private java.math.BigDecimal amount;
        private String currency;
        private Long fromWalletId;
        private Long toWalletId;
        private String initiatedAt;
        private String completedAt;
        private String failureReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaginationMetadata {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }
}

