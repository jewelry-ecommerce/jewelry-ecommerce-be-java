package com.pk.core.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phản hồi khi khách gửi yêu cầu (back-in-stock, order-support, newsletter). Kế thừa {success, message} nên FE cũ
 * vẫn đọc được; thêm cờ để phân biệt "đã lưu yêu cầu" với "đã gửi thông báo": `recorded` = yêu cầu đã được lưu,
 * `notificationSent` = đã có thông báo thật gửi tới khách. Hệ thống CHƯA có bộ gửi SMS/email nên luôn false.
 */
@Getter
@Setter
@NoArgsConstructor
public class CustomerRequestAckResponseDto extends MessageResponseDto {

    private boolean recorded;

    private boolean notificationSent;

    public CustomerRequestAckResponseDto(String message) {
        super(true, message);
        this.recorded = true;
        this.notificationSent = false;
    }
}
