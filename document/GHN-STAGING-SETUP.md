# GHN Staging — đăng ký & gắn vào web đồ án

Staging **miễn phí**, không tạo đơn ship thật, không trừ tiền.

## 1) Đăng ký tài khoản staging

1. Mở https://5sao.ghn.dev (hoặc SSO staging GHN) → đăng ký bằng SĐT.
2. Developer portal staging: https://developer.ghn.dev → Đăng nhập cùng SĐT.
3. Menu tài khoản → **Quản lý token** → hiện Token (OTP về SĐT) → copy.
4. Lấy ShopId: gọi API (thay TOKEN):

```bash
curl -s "https://dev-online-gateway.ghn.vn/shiip/public-api/v2/shop/all" -H "Token: TOKEN"
```

Field `_id` của shop = `GHN_SHOP_ID`.  
Nếu chưa có shop, tạo cửa hàng trên portal staging rồi gọi lại.

5. Ghi lại:
   - `GHN_TOKEN`
   - `GHN_SHOP_ID`
   - District/Ward lấy hàng (mặc định BE: district `1442`, ward `21211` — đổi nếu shop khác)

## 2) Gắn env trên Render (BE)

```
GHN_ENABLED=true
GHN_TOKEN=...
GHN_SHOP_ID=...
GHN_BASE_URL=https://dev-online-gateway.ghn.vn
GHN_FROM_DISTRICT_ID=1442
GHN_FROM_WARD_CODE=21211
```

Deploy lại BE. Không commit token vào git.

## 3) Cách chứng minh

1. Client đặt đơn (COD hoặc PayOS).
2. Admin: Chờ xác nhận → Đã xác nhận → **Đang giao**.
3. BE gọi GHN tạo vận đơn → `trackingCode` hiện trên chi tiết đơn.
4. Vào portal GHN staging → thấy đơn/mã vận đơn tương ứng.

Khi `GHN_ENABLED=false` (mặc định): vẫn đổi status được, mã vận đơn dạng `MOCK-...`.

## 4) Lưu ý

- Token staging ≠ production. Đừng trỏ production khi demo đồ án.
- Webhook cập nhật status GHN → OMS: chưa làm trong lượt này; admin vẫn đổi `DELIVERED` tay.
