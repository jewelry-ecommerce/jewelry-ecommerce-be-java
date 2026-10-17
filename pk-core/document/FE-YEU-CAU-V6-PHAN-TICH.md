# Phân tích yêu cầu FE (backend-v6) → việc BE cần làm

Nguồn: `KLTN/yeu cau/backend-v6/*.md` + code FE mới (admin/client). Chỉ phân tích, chưa code.

## A. BE nên làm ngay (rõ hợp đồng, effort nhỏ)
| # | Việc | Bằng chứng FE | Việc BE |
|---|------|---------------|---------|
| 1 | Snapshot SKU ở dòng đơn (OMS) | FE list đọc `item.skuCode` | Migration V28 thêm `sku_code` (+ snapshot thuộc tính) vào `order_items`, set lúc tạo đơn, trả `skuCode` ở `OrderItemResponseDto`, test đổi SKU sau không ảnh hưởng đơn cũ |
| 2 | `shippingAddressSnapshot.firstName` | FE chỉ hiển thị `firstName` | Đưa họ tên đầy đủ vào `firstName` |
| 3 | Banner: DELETE placement | banner.api.ts gọi xoá placement | Thêm endpoint + rule + test |
| 4 | Header: `conflictingHeaders[]` có cấu trúc | FE đang stub `[]` | Trả danh sách header xung đột thay vì chỉ message |
| 5 | Sort sản phẩm phía server | FE hiện chỉ sort 100 bản ghi đầu | Hỗ trợ `orderBy/orderType` (createdAt/updatedAt) ở list admin |
| 6 | Filter bổ sung | badge-flow `displayStatus`; job theo `referenceType/referenceId` | Thêm query param |

## B. Làm tiếp, khối lượng lớn (hợp đồng đã có trong docs)
| # | Việc | Ghi chú |
|---|------|---------|
| 7 | CMS Navigation admin (cây DRAFT/PUBLISHED, CRUD, reorder, archive, publish, publish-status, clear cache, lịch badge + conflict, sâu ≤4) + public `cms/storefront/navigation`, `/plp-rail` | FE admin hiện 501; client có fallback menu tĩnh nên không chặn |
| 8 | Search-pinned admin CRUD (chỉ landing pin: keywordPattern null, position 0–3 duy nhất, start bắt buộc, end tuỳ chọn, isActive) | Client chưa có nơi dùng |
| 9 | Product-list banner config API | FE gọi nhưng BE chưa có |

## C. Cần chủ dự án quyết định
- Lưu `createdById` (người tạo) cho sản phẩm/đơn để filter theo người — có/không?
- Footer: thêm `newsletterHeading/Subheading`?
- Banner `revision` / lịch sử phiên bản (FE đang dùng `revision ?? 0`)?
- Promotion wizard (~45 endpoint draft/schema/publish/lifecycle/simulate/voucher; FE bật MARKDOWN, FLAT_PRICE, VOUCHER) — phạm vi? Overview cần thêm `createdAt`.
- OMS nâng cao (tạo/sửa/clone đơn admin, ghi chú, xác nhận thanh toán, workflow đổi/trả, hoá đơn, audit log) — FE chưa nối hết.

## D. Không phải việc BE / ngoài phạm vi
- Test trình duyệt (CRUD/upload/blocks/schedules), CORS deploy trực tiếp, xác minh label: việc FE/test.
- Ngoài phạm vi: thanh toán online, GHN/3PL, hoá đơn VAT, set/review/packaging, Fast FBO, import xlsx.
- Menu sidebar còn kho/POS/loyalty/hỗ trợ khách… là module legacy; chưa xác nhận chúng có chạy ở chế độ Java (nhiều khả năng 501).

## Đề xuất thứ tự
A(1→6) → B(9, 8, 7) → C sau khi có quyết định.
