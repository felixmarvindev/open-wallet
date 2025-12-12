package com.openwallet.customer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycInitiateRequest {

    @NotNull(message = "Documents are required")
    @NotEmpty(message = "At least one document must be provided")
    private Map<String, Object> documents;
}

