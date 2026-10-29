# Tích hợp v8 — 29/10/2026

Nguồn partner: `khanh-duy-003/kltn_ecommerce`, commit `3844d77` (`v8`).
Repo triển khai: `jewelry-ecommerce/jewelry-ecommerce-be-java`.
Giữ `origin` trỏ repo triển khai; remote `partner` chỉ dùng để lấy cập nhật.

## Phần được hợp nhất

- Điều chỉnh kho: kiểm tra dấu, chống tràn/âm tồn, bảo vệ tồn giữ chỗ và idempotency; lịch sử điều chỉnh qua API movements.
- Search: trending, gợi ý, lịch sử cá nhân; bảo vệ lịch sử bằng đăng nhập.
- CMS: kiểm tra cấu hình các loại block và trả đường dẫn field bị lỗi.
- Thanh toán: storefront chỉ nhận COD; callback từ chối khi chưa cấu hình secret.
- CSV import/export: tách thư mục riêng tư, tải bằng link ký có hạn.
- Yêu cầu khách: phân biệt đã ghi nhận và chưa gửi SMS/email.
- Migration mới V37 (inventory_movements), V38 (search_keywords, search_history). Không sửa migration cũ.

## Giải quyết xung đột với bản deploy

- Giữ Dockerfile gốc, workflow build nhanh, Render Singapore và profile `cloud` kết nối Neon.
- Giữ Cloudinary cho ảnh/video; không chuyển ảnh cloud về ổ đĩa tạm của Render.
- LocalFileStorageService dùng UploadStorage v8 để chia thư mục images/videos, đồng thời giữ điều kiện provider để tránh hai bean FileStorageService.
- Local và Cloudinary cùng dùng UploadMediaValidator/SVG allowlist; nhận giới hạn SVG 512 KB và mã lỗi FILE_REJECTED của v8.
- Giữ chặn truy cập công khai CSV và header bảo vệ SVG trong UploadWebConfig.

## Phần vẫn cần làm riêng

- Client cần nối alias API search theo mục 4c trong BE-THAY-DOI-2026-10-28.md.
- Admin cần nối màn lịch sử kho, reference/note và Idempotency-Key.
- Sổ kho v8 chỉ ghi điều chỉnh thủ công; chưa ghi biến động do đơn hàng/giao/trả, không backfill.
- Catalog chậm chưa được tối ưu; OTP/SMS/email thật chưa có provider.
- CSV vẫn dùng ổ tạm trên Render Free: sau restart có thể cần xuất lại. Ảnh Cloudinary không chịu giới hạn này.
- Bản v8 có thư mục deploy Oracle để tham khảo; Render vẫn dùng Dockerfile gốc và profile cloud.

## Kiểm chứng

- Maven package pk-service và các module phụ thuộc: BUILD SUCCESS (Java 17).
- 154 test / 17 bộ tập trung: 0 failure, 0 error (kho, search, CMS, CSV, SVG, Cloudinary, yêu cầu khách và OMS).
- Test dùng H2 riêng; không chạy seed/reset hoặc test ghi dữ liệu lên Neon.
Sau deploy, cần kiểm tra health và luồng demo trên domain thật; build/test local không thay thế kiểm tra này.
