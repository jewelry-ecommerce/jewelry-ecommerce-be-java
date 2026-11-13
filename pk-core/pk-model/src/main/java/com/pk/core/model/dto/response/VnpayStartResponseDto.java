package com.pk.core.model.dto.response;

public record VnpayStartResponseDto(String orderCode, String payUrl, boolean redirect) {
}
