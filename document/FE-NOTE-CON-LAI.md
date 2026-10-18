# Ghi chú gửi FE: BE đã làm gì, FE cần nối gì, còn thiếu gì

Cập nhật 2026-10-17. Chi tiết từng API: `FE-API-CONTRACT.md` (sinh tự động từ source). Quy tắc nghiệp vụ: `FE-HANDOVER-REPORT.md`.
Test BE: **353/353 pass** trên H2 và trên PostgreSQL 16.2 thật (Flyway V1→V36 cài mới). `verify-java-v7.mjs` của FE chưa chạy lại.
Tiền tố `/api/v1`; thành công `{ "data": ... }`; lỗi `{ status, message, errors:[{code,message}], dependencies? }` kèm HTTP status thật. Id luôn là số (FE tiếp tục `toUiEntityIds`).

## A. BE đã có thêm (đợt FE v6) — FE bỏ chặn "Java chưa có API" và nối

### A1. Bảng map đường dẫn FE → Java
| FE gọi (hệ cũ) | Java |
|---|---|
| `cms/admin/navigations*` | `/admin/cms/navigations*`; storefront `/storefront/cms/navigation`, `/storefront/cms/navigation/plp-rail` |
| search-pinned | `/admin/catalog/search-pinned-products` |
| banner product-list config | `/admin/banner/product-list-configs`; storefront `/storefront/banner/product-list?conditionType&targetSlug` |
| `promotion/promotion-drafts*` | `/admin/promotions/drafts*` |
| `promotion/promotions*` (đã phát hành) | `/admin/promotions/published*` |
| `promotion/promotion-campaigns` (overview) | `/admin/promotions/campaigns` |
| `promotion/internal/promotion-runtime/evaluate` | `/admin/promotions/runtime/evaluate` |
| `promotion/promotion-vouchers/{discover,validate-code}` | `/admin/promotions/vouchers/{discover,validate-code}` (ORDER_MANAGER dùng `/admin/orders/promotion-vouchers/{discover,validate-code}`) |
| `promotion/admin/customer-audience/by-phone` | `/admin/promotions/customer-audience/by-phone` |
| `order/orders/*` (admin) | `/admin/orders/*` cùng đường dẫn con (`audit-logs`, `pricing-context/quote`, `duplicate`, `{id}`, `{id}/confirm-payment`, `{id}/notes`) |
| `order/order-returns*` | `/admin/order-returns*` (danh sách là `POST /admin/order-returns/list`) |
| `order/admin/order-returns/{quote,commit}` | `/admin/order-returns/{quote,commit}` |

### A2. OMS nâng cao (mới, V34) — quyền ADMIN, ORDER_MANAGER
- **Báo giá + tạo đơn thủ công**: `POST /admin/orders/pricing-context/quote` rồi `POST /admin/orders` (body V2: `receiverPhone, items[{variationId (=id SKU), quantity}], firstName, provinceName, wardName, addressLine, couponCodes, shippingMethod, note, contactEmail, idempotencyKey, pricingEvaluationId, expectedGrandTotalMinor`). Chỉ **COD**, phí ship 0, mã tỉnh/phường (GHN) được bỏ qua — gửi tên. SĐT trùng khách có tài khoản thì đơn gắn vào khách đó, ngược lại là khách vãng lai. `source = ADMIN`, `createdById` = nhân viên tạo. Giá lệch báo giá → 409 `ORDER_PRICING_CHANGED`; cùng `idempotencyKey` → trả lại đơn cũ.
- **Sửa đơn** `PUT /admin/orders/{id|mã}`: chỉ khi `PENDING/CONFIRMED`; đổi `items` chỉ khi `PENDING` (tự nhả/giữ chỗ tồn, kiểm tra lại voucher). Không đổi được phương thức thanh toán khác COD.
- **Nhân bản** `POST /admin/orders/duplicate` (`replaceSourceAfterDuplicate=true` thì huỷ đơn nguồn), **xác nhận thanh toán** `PUT /admin/orders/{id}/confirm-payment` (UNPAID→PAID, gọi lại → 409).
- **Ghi chú** `GET|POST /admin/orders/{id}/notes` (`type` = `order|internal`, chỉ thêm).
- **Audit log** `GET /admin/orders/audit-logs?entityName=oms|order_refund&entityId&page&take` → `{list,total,page,take}`; mỗi dòng có `actor`, `changes[{field,oldValue,newValue}]`. Đã ghi cho: tạo/sửa/nhân bản đơn, ghi chú, xác nhận thanh toán, đổi trạng thái, huỷ, trả hàng, hoàn tiền, các bước đổi/trả. `wms`: không có dữ liệu.
- **Đổi/trả** `POST /admin/order-returns` (hoặc `quote` → `commit`), `POST .../list` (trả `summary`), `GET|PUT .../{id|mã RT…}`, `PUT .../{id}/approve|reject`. Đơn phải `DELIVERED`; `type` = `Refund|Exchange`; số lượng trả không vượt phần còn lại (đã trừ yêu cầu đang chờ). Trạng thái: `Pending → Completed` (duyệt) / `Rejected` (từ chối, bắt buộc lý do) / `Cancelled` (PUT `status=Cancelled`). **Duyệt** = nhập kho hàng trả + cập nhật đơn gốc (`RETURNED` khi trả hết) + hoàn tiền (Refund: đúng giá trị đã trả sau khuyến mãi; chỉ khi đơn đã PAID, không vượt số còn hoàn được) + với Exchange tạo **đơn thay thế** (`exchangeFulfillmentOrderId`), giá trị hàng trả được trừ vào đơn mới, nếu hàng đổi rẻ hơn thì hoàn phần chênh.

### A3. Đã làm trước đó trong đợt v6 (xem contract)
Snapshot SKU ở dòng đơn (`skuCode`, `sizeLabel`, `metalColorLabel`); `shippingAddressSnapshot.firstName` = họ tên đầy đủ; xoá placement banner; header trùng lịch trả `dependencies[]`; sort sản phẩm `orderBy/orderType`; lọc `createdById` cho đơn **và sản phẩm** (BE lưu người tạo); footer `newsletterHeading/newsletterSubHeading`; banner layout có `revision`; Navigation (cây DRAFT/PUBLISHED, publish, archive, reorder, badge có lịch → 409 `NAVIGATION_BADGE_SCHEDULE_CONFLICT` kèm `dependencies`); Search-pinned; Product-list banner config.

### A4. Wizard khuyến mãi (V33, ~45 endpoint) — giới hạn đã chốt
- Loại schema: `MARKDOWN`, `FLAT_PRICE`, `VOUCHER`. Điều kiện: `ALWAYS`; `TARGET_SUBTOTAL` (chỉ VOUCHER, chỉ `GREATER_THAN_OR_EQUAL`). Lợi ích: giảm `PERCENT|FIXED_AMOUNT` (+ trần), hoặc giá đồng giá. Đơn vị tiền = đồng (`amountMinor` là VND).
- Target: `ALL|CATEGORY|COLLECTION|MANUAL` (không `UPLOAD_FILE`); audience chỉ `ALL`; applicability chỉ `ONLINE`; `lineFilter` chỉ `ANY`; **không có quota** (`quota-statistics` trả mảng rỗng). Chọn SKU được hiện thực ở mức sản phẩm.
- Xoá nháp = chuyển `DISCARDED`. Phát hành → tạo khuyến mãi/voucher thật cho engine giá hiện có. Tạm dừng/tiếp tục/kết thúc/bỏ phát hành schema đổi trạng thái hàng thật.
- Endpoint v1 cũ (`promotion/promotions` kiểu legacy) không làm vì FE không gọi.

### A5. Sửa lỗi FE v7 báo (V35) — xem `FE-HANDOVER-REPORT.md` mục 8
- Duyệt/từ chối/huỷ đổi-trả song song: chỉ 1 request thắng, các request còn lại 409.
- Duyệt đổi hàng khi giá hàng đổi lệch báo giá: 409 `ORDER_RETURN_QUOTE_STALE`, yêu cầu vẫn `Pending` => FE báo giá lại bằng `POST /admin/order-returns/quote` với `returnId`, rồi `PUT /admin/order-returns/{id}` kèm `pricingQuote`.
- Voucher chiến dịch giữ target SKU ở mọi đường báo giá/đặt hàng; lỗi mới `VOUCHER_NO_ELIGIBLE_ITEMS` (giỏ không có dòng đủ điều kiện). Phát hành voucher có target rỗng => `PROMOTION_TARGET_EMPTY`.
- Import CSV tạo sản phẩm mới chạy được trong giao dịch (đã thử end-to-end trên PostgreSQL).
- Khuyến mãi MARKDOWN/FLAT_PRICE chọn SKU nào thì chỉ SKU đó được giảm (SKU anh em cùng sản phẩm không còn bị giảm lây) — V36.
- Sửa đơn admin song song với huỷ/sửa khác: xếp hàng hoặc 409 `INVALID_STATUS_TRANSITION`.
- ORDER_MANAGER tra khách: `GET /admin/orders/customer-audience/by-phone?phone=` (đường dẫn `/admin/promotions/...` vẫn chỉ ADMIN/CATALOG_MANAGER).
- Xoá vị trí banner còn block CMS dùng: 409 `PLACEMENT_IN_USE_BY_CMS`.
- Thành công không có dữ liệu thì body KHÔNG có khóa `data` (Jackson non_null): FE coi là `null`.

## B. FE cần làm
1. Áp bảng A1: viết/sửa adapter Java cho Navigation, Search-pinned, Product-list banner, Promotion wizard, OMS nâng cao; ngừng ném "Java chưa có API" cho các tính năng trên.
2. Đọc `dependencies` trong body lỗi (header trùng lịch, navigation badge, các lỗi `*_IN_USE`) để hiện xác nhận.
3. Dùng `GET /admin/promotions/campaigns` (overview) và `DELETE .../drafts/{id}`; `deletePromotionDraft`, `getCampaignOverview` đang dùng adapter cũ.
4. `order-java.adapter.ts`: `createdById` giờ được hỗ trợ; vẫn chưa có `warehouseId`.
5. Các mục đã báo ở đợt trước (order list/counts, Idempotency-Key, Dashboard, Header/Footer/Logo, Brand/Season, CORS…) xem `FE-HANDOVER-REPORT.md` mục 5.

## C. BE chưa có (ngoài phạm vi COD/đồ án) — FE giữ thông báo "chưa hỗ trợ"
| Hạng mục | Lý do |
|---|---|
| Thanh toán online: `check-paid`, `retry-payment` | Chỉ COD |
| Hãng vận chuyển (GHN/3PL): dịch vụ, tính phí, mã tỉnh/phường | Ngoài phạm vi; phí ship = 0 |
| Hoá đơn VAT (`order/order/invoices*`) | Ngoài phạm vi |
| Xoá đơn (`DELETE order/orders/{id}`) | Bảng đơn không xoá mềm; dùng huỷ đơn |
| Quy trình đổi/trả đầy đủ: lấy hàng, kiểm hàng, vận chuyển trả/đổi, `type=Compensation` | Chỉ có Pending → Completed/Rejected/Cancelled |
| Kho (`warehouseId`), Fast FBO, đánh giá sản phẩm, set/packaging | Hệ thống 1 kho / ngoài phạm vi |
| Quota khuyến mãi, audience theo nhóm khách, target `UPLOAD_FILE` | Chưa làm |
| `tenantCode` | BE Java không có tenant; response wizard trả cố định `"default"` |

## D. Phía owner (chưa kiểm chứng)
1. Chạy Flyway V12–V34 và `mvn test` trên **PostgreSQL thật**, gửi log nếu lỗi (test hiện mới chạy trên H2).
2. Kiểm tra trình duyệt thật khi deploy trực tiếp (CORS, đăng nhập).
3. Dashboard `topProducts`/`lowStock` tính bằng Java, đủ cho quy mô đồ án.
4. Dọn file tạm trong `KLTN/claude/` (`review-src.tgz`, `_tl/`) và file rỗng `CollectionRepo.java.tmp` trong `pk-business`.
