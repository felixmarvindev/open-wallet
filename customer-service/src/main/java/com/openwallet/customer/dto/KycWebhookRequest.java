package com.openwallet.customer.dto;

import com.openwallet.customer.validation.KycStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycWebhookRequest {

    @NotBlank(message = "Provider reference is required")
    private String providerReference;

    @NotBlank(message = "Status is required")
    @KycStatus
    private String status; // VERIFIED or REJECTED

    private String verifiedAt;

    private String rejectionReason;

    @NotNull(message = "Customer ID is required")
    private Long customerId;
}

