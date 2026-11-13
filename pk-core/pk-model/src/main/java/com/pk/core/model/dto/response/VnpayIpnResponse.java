package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;

public record VnpayIpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {
    public static VnpayIpnResponse confirmed() {
        return new VnpayIpnResponse("00", "Confirm Success");
    }

    public static VnpayIpnResponse unknown() {
        return new VnpayIpnResponse("99", "Unknown error");
    }

    public static VnpayIpnResponse from(BusinessException error) {
        if (ErrorCode.NOT_FOUND.equals(error.getCode())) return new VnpayIpnResponse("01", "Order not found");
        if (ErrorCode.PAYMENT_ALREADY_PROCESSED.equals(error.getCode())) return new VnpayIpnResponse("02", "Order already confirmed");
        if (ErrorCode.PAYMENT_AMOUNT_MISMATCH.equals(error.getCode())) return new VnpayIpnResponse("04", "Invalid amount");
        if (ErrorCode.PAYMENT_SIGNATURE_INVALID.equals(error.getCode())) return new VnpayIpnResponse("97", "Invalid signature");
        return unknown();
    }
}
