package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VnpayDemoConfirmRequestDto(
        @NotBlank @Size(max = 100) String orderCode,
        @NotBlank @Size(max = 100) String txnRef,
        @NotNull @Positive BigDecimal amount,
        boolean success,
        @NotBlank @Size(max = 256) String sig) {
}
