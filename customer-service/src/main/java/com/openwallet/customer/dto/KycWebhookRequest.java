package com.openwallet.customer.dto;

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

    @NotBlank
    private String providerReference;

    @NotBlank
    private String status; // VERIFIED or REJECTED

    private String verifiedAt;

    private String rejectionReason;

    @NotNull
    private Long customerId;
}

