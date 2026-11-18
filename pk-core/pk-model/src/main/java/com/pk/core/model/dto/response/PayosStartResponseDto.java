package com.pk.core.model.dto.response;

public record PayosStartResponseDto(String orderCode, String payUrl, boolean redirect) {
}
