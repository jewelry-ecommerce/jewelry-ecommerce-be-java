package com.pk.core.model.dto.response;

public record VnpayReturnDto(String orderCode, boolean success, String responseCode, String message, String redirect) {
}
