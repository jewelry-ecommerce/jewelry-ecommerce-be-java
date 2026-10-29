package com.pk.core.model.dto.response;

/** File CSV của một job import/export để trả về cho trình duyệt tải xuống. */
public record IeFileDto(String fileName, byte[] content) {
}
