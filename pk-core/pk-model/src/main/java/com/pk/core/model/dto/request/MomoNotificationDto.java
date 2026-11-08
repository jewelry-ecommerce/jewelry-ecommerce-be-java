package com.pk.core.model.dto.request;

import jakarta.validation.constraints.*;
import java.util.Map;

public record MomoNotificationDto(
        @NotBlank String partnerCode, @NotBlank String orderId, @NotBlank String requestId,
        @NotNull @Positive Long amount, @NotBlank String orderInfo, @NotBlank String orderType,
        @NotNull Long transId, @NotNull Integer resultCode, @NotNull String message,
        @NotNull String payType, @NotNull Long responseTime, @NotNull String extraData,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String signature) {
    public Map<String, Object> signatureFields(String accessKey) {
        return Map.ofEntries(Map.entry("accessKey", accessKey), Map.entry("amount", amount),
                Map.entry("extraData", extraData), Map.entry("message", message), Map.entry("orderId", orderId),
                Map.entry("orderInfo", orderInfo), Map.entry("orderType", orderType), Map.entry("partnerCode", partnerCode),
                Map.entry("payType", payType), Map.entry("requestId", requestId), Map.entry("responseTime", responseTime),
                Map.entry("resultCode", resultCode), Map.entry("transId", transId));
    }
}
