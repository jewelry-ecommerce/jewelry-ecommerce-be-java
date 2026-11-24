package com.pk.core.business.shipping;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GHN staging/production — đồ án mặc định staging (free, không phát sinh đơn thật).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ghn")
public class GhnProperties {
    /** Bật gọi API GHN; tắt thì fee=0 và tạo vận đơn giả. */
    private boolean enabled = false;
    private String token = "";
    private long shopId = 0;
    /** Staging: https://dev-online-gateway.ghn.vn */
    private String baseUrl = "https://dev-online-gateway.ghn.vn";
    private int serviceTypeId = 2;
    private int defaultWeightGrams = 200;
    private int defaultLengthCm = 20;
    private int defaultWidthCm = 15;
    private int defaultHeightCm = 10;
    /** Mã quận/huyện kho lấy hàng (staging). */
    private int fromDistrictId = 1442;
    private String fromWardCode = "21211";

    public boolean ready() {
        return enabled && token != null && !token.isBlank() && shopId > 0
                && baseUrl != null && baseUrl.startsWith("https://");
    }
}
