# Báo cáo BE → FE (đợt xử lý `00-BAN-GIAO-FINAL-CHO-AGENT-BE`)

Phạm vi: chỉ API/BE Java (ID số, COD, không đổi UI, không seed/reset DB, không sửa migration đã áp, không commit/push).
Tiền tố `/api/v1`. Admin: `/admin/...`; storefront: `/storefront/...`. Thành công `{ "data": ... }`; lỗi `{ status, message, errors:[{code,message}] }` kèm **HTTP status thật**.
Contract chi tiết từng API (method, path, auth, query, body, header, field + ràng buộc) nằm ở **`FE-API-CONTRACT.md`** (sinh tự động từ source, luôn khớp code).

## 1. Kết quả build / test

| Hạng mục | Kết quả | Môi trường |
|---|---|---|
| Biên dịch 7 module | OK | JDK 21, `--release 17` (cloud, không qua Maven) |
| Unit + Integration test | **340 / 340 pass** | H2 (MODE=PostgreSQL), cloud |
| Migration V12–V34 trên PostgreSQL thật | **CHƯA kiểm chứng** | cloud không có PostgreSQL; cần chạy Flyway + `mvn test` trên máy chủ dự án |
| `mvn test` trên máy chủ dự án | cần chạy lại | chưa xác nhận sau các thay đổi đợt này |

Kết quả H2 không thay thế kiểm tra PostgreSQL. Không báo "full done".

## 2. Phân loại

**BE lỗi (đã sửa, có test)**
- P0.1 `GET /admin/orders` rỗng khi bỏ filter → chuẩn hoá null/rỗng, `status`/`paymentStatus` không phân biệt hoa thường (`OmsOrderListFilterIT`).
- P0.2 HTTP 201 kèm body lỗi → `RestErrorStatusAdvice` ép đúng status 4xx/5xx (`OmsOrderListFilterIT`, các IT 401/403/404/409).
- P0.3 carousel `CAMPAIGN` nhận `campaignId` hoặc `promotionId` (số hoặc chuỗi số); sai kiểu / không tồn tại → 400 `CAROUSEL_SOURCE_INVALID` (`CmsPublishV5IT`).
- PUT biến thể trả đủ `costPrice` (admin view); trùng `code` danh mục báo `DUPLICATE_CODE` trước slug; tra slug sản phẩm/banner không tồn tại không làm hỏng giao dịch (rollback-only).
- Khuyến mãi/voucher: dữ liệu sai trước đây rơi xuống lỗi ràng buộc DB → nay 400 `DISCOUNT_INVALID` (xem 4.4).

**Mới thêm đợt này (API trước đó chưa có)**
- Dashboard: `/admin/dashboard/{overview,revenue,recent-orders,low-stock,top-products}` (không cần migration).
- Idempotency-Key khi đặt đơn (member + guest) — V25.
- Header (thanh thông báo) / Footer / Logo + public `/storefront/cms/site-layout` — V26.

**API đã có, FE chưa nối**
- Banner adapter FE còn ép `CAROUSEL`.
- FE danh sách đơn còn workaround gửi tham số rỗng (giờ không cần nữa).
- Client chưa gửi `Idempotency-Key`.
- Trang admin Header/Footer/Logo (`trading-ui/.../site-layout`) gọi API kiểu `cms/admin/headers|footer|logos` của hệ khác (`{list,total}`, id chuỗi, `tenantCode`), **chưa nối Java**; cần adapter (mục 5).
- Navigation / Search-pinned / Product-list banner: BE đã có từ đợt FE v6 (mục 7).

## 3. Migration đã thêm (không sửa migration đã áp)
V12 cms_visibility_seo · V13 sku_attribute_values · V14 product_media · V15 guest_orders · V16 oms_fulfillment_returns_refunds · V17 banner_layouts · V18 pim_extended_fields · V19 preorder_scope · V20 category_code_attribute_values · V21 product_lifecycle_status · V22 cms_draft_publish · V23 product_approval · V24 import_export · **V25 order_idempotency** · **V26 site_layout** · **V27 brand_season** (bảng `brands`, `seasons`; cột `products.brand_id`, `products.season_id`; không chèn dữ liệu mẫu). Tất cả **chưa chạy trên PostgreSQL thật**.

## 4. Quy tắc nghiệp vụ chốt

### 4.1 OMS
- (Đợt 3, mục 7) Admin tạo/sửa/nhân bản đơn, ghi chú, audit log, đổi/trả đã có.
- `GET /admin/orders?search&status&paymentStatus&fromDate&toDate&page=1&take=20`: mọi tham số tuỳ chọn, rỗng = không lọc; ngày `yyyy-MM-dd` (sai → 400); `take` tối đa 100.
- Hoàn tiền: một phần → `paymentStatus` vẫn `PAID`, `refundedAmount` cộng dồn, `refundableAmount` = tổng − đã hoàn; hoàn đủ → `REFUNDED`; vượt → 400; đã `REFUNDED` hoàn thêm → 409.
- COD tự `PAID` khi `DELIVERED`. Huỷ (khách/admin/status) nhả tồn + voucher **đúng một lần**.
- Kiểm chứng đồng thời (`OmsConcurrencyIT`): 8 request đặt hàng khi tồn 3 → đúng 3 đơn, tồn = 0 (không âm); 6 request huỷ cùng đơn → 1 thành công, tồn/voucher nhả một lần; 5 request hoàn 60% → chỉ 1 thành công, không vượt tổng.

- **Bổ sung theo yêu cầu FE (đợt 2)** — `GET /admin/orders` nhận thêm: `skuCode` (khớp một phần, không phân biệt hoa/thường), `shippingMethod` (`Standard`=`STANDARD`, `StorePickup`=`STORE_PICKUP`, bỏ qua hoa/thường & gạch), `provinceName` (chứa), `source` (`WEBSITE|ONLINE`; giá trị khác → rỗng), `dateField` (`createdAt` mặc định | `deliveredAt`), `orderBy` (`createdAt|updatedAt|orderCode|grandTotal`) + `orderType` (`ASC|DESC`, mặc định `DESC`); giá trị sai → 400.
- `GET /admin/orders/counts` (cùng bộ lọc, bỏ `status`): `{ total, byStatus:{PENDING,CONFIRMED,SHIPPING,DELIVERED,CANCELLED,RETURNED} }` — thay cho N request đếm.
- Mọi endpoint `/admin/orders/{x}` (chi tiết, status, cancel, return, refund) nhận **mã đơn hoặc id số**.
- Đơn trả thêm: `customerId` (null = khách vãng lai), `contactEmail`, `deliveredAt`, `codAmount` (= tổng đơn nếu COD, ngược lại 0). Field null bị bỏ khỏi JSON.
- `GET /admin/catalog/products?stockStatus=IN_STOCK|LOW_STOCK|OUT_OF_STOCK` (tổng tồn khả dụng các SKU: 0 = hết, 1–5 = sắp hết). FE hiện chỉ có enum `IN_STOCK|OUT_OF_STOCK`.
- **Thương hiệu & Mùa (V27)**: `GET|POST /admin/catalog/brands`, `GET|PATCH|DELETE /admin/catalog/brands/{id}`; tương tự `/admin/catalog/seasons`. Brand: `{id, code, name, image, websiteUrl, status: ACTIVE|INACTIVE}` (`code` tự IN HOA, duy nhất; 409 `BRAND_CODE_DUPLICATE`; xoá mềm, 409 `BRAND_IN_USE` nếu còn sản phẩm). Season: `{id, code, name, description, startMonth, endMonth (1..12, được vắt năm), sortOrder, isActive}` (409 `SEASON_CODE_DUPLICATE`, `SEASON_IN_USE`). Lỗi nhập liệu 400 `BRAND_INVALID` / `SEASON_INVALID`. Danh sách trả `PageResponse`; `status` nhận `ACTIVE|INACTIVE|ALL` (không phân biệt hoa/thường), season nhận `isActive`.
- Sản phẩm: create/PUT nhận `brandId`, `seasonId` (số, tuỳ chọn; **PUT không gửi = bỏ gán**; id không tồn tại hoặc thương hiệu/mùa đang ngừng → 400, trừ khi sản phẩm đã gắn sẵn). Response trả `brandId, brandName, brandCode, brandImage, seasonId, seasonName`; `brand{}` trả thương hiệu thật nếu đã gán, chưa gán thì giữ mặc định cửa hàng. List/counts lọc `brandId`, `seasonId` (FE gửi `seasonUuid` cũng được, giá trị là id số).
- **Chưa hỗ trợ**: `warehouseId` (chỉ 1 kho). `createdById` (đơn & sản phẩm) đã được lưu và lọc từ đợt FE v6.

### 4.2 Idempotency-Key (đặt đơn)
- Header `Idempotency-Key` (tuỳ chọn) cho `POST /storefront/order` và `POST /storefront/order/guest`. Định dạng 8–100 ký tự `[A-Za-z0-9._:-]`, sai → 400 `IDEMPOTENCY_KEY_INVALID`.
- Cùng khoá + cùng nội dung → trả lại **đúng đơn đã tạo** (không giữ chỗ tồn / dùng voucher thêm); cùng khoá + nội dung khác → 409 `IDEMPOTENCY_KEY_REUSED`.
- Phạm vi theo người đặt (member `U:<id>`, guest `G:<sđt>`); hiệu lực **24 giờ**; không gửi khoá = hành vi cũ (mỗi lần một đơn).
- Gửi đồng thời cùng khoá: UNIQUE `(owner, idem_key)` + thử lại một lần → mọi request nhận cùng một đơn (`OrderIdempotencyIT`, 4 luồng song song).
- CORS đã cho phép header này.

### 4.3 Dashboard (`ADMIN`, `ORDER_MANAGER`)
- `range` = `LAST_7_DAYS | LAST_30_DAYS | LAST_12_MONTHS` (mặc định 30 ngày; sai → 400). Shape khớp `DashboardOverview` của FE.
- Doanh thu ròng = Σ(`grandTotal` − `refundedAmount`) đơn `CONFIRMED/SHIPPING/DELIVERED` theo ngày đặt; `deltaPercent` so kỳ liền trước cùng độ dài (null nếu kỳ trước = 0). Tồn thấp: khả dụng ≤ 5 (trùng ngưỡng `LOW_STOCK`). `channel` luôn `ONLINE`.
- Ghi chú phạm vi: tài liệu FE 11/12/13 ghi "không thêm dashboard/report riêng"; đợt này làm theo yêu cầu owner, số liệu 100% từ DB (không seed/giả).

### 4.4 Khuyến mãi & Voucher (audit + siết validate; không thêm loại khuyến mãi mới)
- Promotion: `discountType` ∈ `PERCENT|FIXED|FLAT_PRICE`; Voucher: `PERCENT|FIXED` (**FLAT_PRICE bị từ chối**, đúng như FE).
- `PERCENT` ≤ 100; giá trị/tối đa không âm; `endsAt` > `startsAt`; `status` ∈ `DRAFT|PUBLISHED`; promotion cần 1–100 sản phẩm, không trùng, phải tồn tại.
- Voucher: mã chuẩn hoá chữ hoa, `[A-Z0-9_-]{3,40}`, trùng → 409 `DUPLICATE_CODE`; `usageLimit` ≥ 1 và không nhỏ hơn số lượt đã dùng. Voucher xoá là xoá thật (đơn giữ snapshot mã).
- Áp dụng: khuyến mãi theo SP chọn mức giảm lớn nhất (không cộng dồn); voucher: trần `maxDiscountAmount`, không vượt tổng đơn, đơn tối thiểu, hết hạn / chưa tới hạn / hết lượt → 400 (`VOUCHER_*`), nháp/không tồn tại → 404. Có test (`PromotionVoucherRulesIT`, `PromotionPricingTest`, `PromotionCheckoutControllerIT`).
- Khuyến mãi/voucher "kiểu cũ" ở trên vẫn giữ nguyên. **Wizard chiến dịch** (nháp → phát hành) đã làm ở đợt FE v6, xem mục 7.4.

### 4.5 Header / Footer / Logo (`ADMIN`, `CATALOG_MANAGER`)
- Header: `GET|POST /admin/cms/headers`, `GET|PATCH|DELETE /admin/cms/headers/{id}`, `PATCH .../{id}/status`. Trạng thái `DRAFT|ACTIVE|DEACTIVE|SCHEDULED`; `displayStatus` tính theo giờ hiện tại. Trùng lịch với header đang bật khác → 409 `HEADER_SCHEDULE_CONFLICT` (thông điệp liệt kê header trùng); gửi `confirmReplaceActive=true` để chuyển các header đó sang `DEACTIVE`. Xoá mềm.
- Footer: `GET|PUT /admin/cms/footer` (một bản duy nhất; chưa lưu thì `id=null`, mảng rỗng). `socialLinks` ≤ 10, `menuColumns` ≤ 6, mỗi cột ≤ 12 mục; `id` con tự sinh nếu không gửi; sắp theo `orderIndex`.
- Logo: `GET|POST /admin/cms/logos`, `GET|PATCH .../{id}`, `PATCH .../{id}/status`, `PATCH .../{id}/archive`. Mỗi `type` (`HEADER|FOOTER|AUTH|FAVICON`) chỉ một logo `ACTIVE` (kích hoạt logo mới tự tắt logo cũ cùng loại).
- Public: `GET /storefront/cms/site-layout` → `{ header (đang hiển thị hoặc null), footer, logos: { <TYPE>: logo } }`, không cần token.
- Test: `SiteLayoutIT`.

### 4.6 Auth / CORS (audit theo route FE thật)
- Origin cho phép mặc định: `http://localhost:{5173,3000,6100,3100}` + `https://jewelry-ecommerce-client.vercel.app`; cấu hình bằng biến `CORS_ALLOWED_ORIGINS`. **Nếu FE chạy bằng `127.0.0.1` thì phải thêm origin đó**. Không dùng cookie (`allowCredentials=false`, Bearer token) — client phải **không** bật `withCredentials`.
- Header cho phép: `Authorization, Content-Type, Accept, Idempotency-Key, X-Guest-Cart-Id, x-guest-id, Language, x-tenant-code`. FE còn gửi `x-guest-order-access-token` và `x-pre-order-access-token` (token của hệ khác; BE Java không phát hành, tra cứu đơn guest dùng mã đơn + SĐT) — các header này **không được phép**, FE chỉ gửi khi có token nên sẽ không xảy ra; nếu gửi, trình duyệt sẽ chặn ở preflight. Test: `CorsAuthAuditIT`.
- Đăng nhập theo SĐT giữ nguyên; luồng OTP / quên mật khẩu / refresh đã có test `AuthControllerIT`, `PhoneAuthControllerIT`. Kiểm tra trình duyệt thật khi deploy trực tiếp (không qua reverse proxy) vẫn là việc của FE/owner.

## 5. FE cần làm
1. Danh sách đơn: bỏ workaround tham số rỗng. Mọi lỗi 4xx/5xx giờ có HTTP status đúng — không còn "201 + body lỗi".
2. Đặt đơn: sinh `Idempotency-Key` (UUID) **mỗi lần mở checkout**, giữ nguyên khi bấm lại / retry, đổi khi giỏ hoặc địa chỉ đổi; xử lý 409 `IDEMPOTENCY_KEY_REUSED`.
3. Carousel chiến dịch: gửi `promotionId` (hoặc `campaignId`) dạng số.
4. Banner adapter dùng layout (`GRID|SLIDER|DOUBLE_BANNER`) từ BE, bỏ ép `CAROUSEL`.
5. Dashboard: gọi `/admin/dashboard/overview?range=...` (đổi từ mock sang Java); id số → chuỗi qua `toUiEntityIds`.
6. Header/Footer/Logo: viết adapter Java (`/admin/cms/headers|footer|logos`): response là `PageResponse` (`content/totalElements`; `normalizeJavaPage` của FE đã map sang `{list,total}`), id số, không có `tenantCode`; xử lý 409 `HEADER_SCHEDULE_CONFLICT` bằng hộp thoại xác nhận rồi gửi lại với `confirmReplaceActive=true`. Storefront gọi `/storefront/cms/site-layout`.
7. Promotion/voucher form: hiển thị lỗi `DISCOUNT_INVALID` theo `errors[0].message`.
8. Đơn hàng (`order-java.adapter.ts`): bỏ `skuCode`, `shippingMethod`, `provinceName`, `source`, `orderBy`, `orderType`, `dateField` khỏi danh sách "unsupported" và truyền thẳng sang query; dùng `/admin/orders/counts` cho các tab trạng thái; gọi chi tiết/đổi trạng thái bằng id số, không cần duyệt hết trang để tìm mã; map `customerId`, `contactEmail`, `deliveredAt`, `codAmount`.
9. Sản phẩm (`java-product.api.ts`): bỏ `stockStatus`, `brandId`, `seasonUuid` khỏi danh sách unsupported; `brandId: null` cứng trong `java-product.mapper.ts` / `product-backend.mapper.ts` cần map từ `brandId` BE; trang Brand/Season viết adapter Java (`/admin/catalog/brands|seasons`, status `Active`↔`ACTIVE`) (thêm `LOW_STOCK` vào enum nếu muốn dùng).

## 6. Còn thiếu / cần owner chốt
Bản gọn gửi FE: `FE-NOTE-CON-LAI.md`.

1. Chạy migration V12–V34 + toàn bộ test trên **PostgreSQL thật** rồi gửi log.
2. Ngoài phạm vi (đã chốt): thanh toán online, GHN/3PL, hoá đơn VAT, Fast FBO, kho, review sản phẩm, quota khuyến mãi (mục 7).
3. Dashboard `topProducts`/`lowStock` tính trong Java (đủ cho quy mô đồ án); nếu dữ liệu lớn cần chuyển sang SQL tổng hợp.

## 7. Đợt 3 — yêu cầu FE v6 (V28–V34)

Phân tích nguồn: `FE-YEU-CAU-V6-PHAN-TICH.md`. Owner chốt "làm theo nhu cầu FE" cho mục C; những gì ngoài phạm vi COD/đồ án bỏ qua.

### 7.1 Nhóm nhỏ (V28–V29)
- V28 snapshot SKU ở dòng đơn: `order_items.sku_code/size_label/metal_color_label` (đơn cũ backfill best-effort), trả ở `items[]`; đổi SKU sau không đổi đơn cũ.
- `shippingAddressSnapshot.firstName` = họ tên đầy đủ, `lastName` rỗng.
- Banner: `DELETE /admin/banner/placements/{id}`; layout có `revision` (V29, tăng theo từng vị trí).
- Header trùng lịch: 409 `HEADER_SCHEDULE_CONFLICT` kèm `dependencies[]` (danh sách header xung đột).
- Sản phẩm: `orderBy/orderType` (createdAt/updatedAt…) ở server; `createdById` lọc đơn và sản phẩm (BE lưu `created_id` = tài khoản tạo).
- Footer: `newsletterHeading`, `newsletterSubHeading` (V29).

### 7.2 Navigation / Search-pinned / Product-list banner (V30–V32)
- Navigation (V30): cây tối đa 4 cấp, hai lớp DRAFT (admin) / PUBLISHED (storefront). Xuất bản = sao chép cây nháp; id công khai ổn định theo `source_id`. Badge nhỏ có lịch; trùng lịch trong cùng nút → 409 `NAVIGATION_BADGE_SCHEDULE_CONFLICT` kèm `dependencies`, trừ khi `confirmReplaceSchedule=true`. Endpoint: xem contract mục "Điều hướng".
- Search-pinned (V31): chỉ ghim "landing" (keywordPattern null), `position` 0–3 duy nhất, `startDate` bắt buộc, `endDate` tuỳ chọn, `isActive`; đổi chỗ khi trùng vị trí lúc sửa.
- Product-list banner (V32): mỗi `(conditionType, targetSlug)` tối đa một cấu hình; `banners` là mảng JSON (ảnh, lịch, hành động `LINK_URL`/`MIX_MATCH`).

### 7.3 OMS nâng cao (V34)
Bảng mới: `order_notes`, `order_audit_logs`, `order_returns`, `order_return_items`, `order_return_status_history`; cột `orders.source` (`WEBSITE|ADMIN`).
- **Tạo đơn thủ công**: báo giá `POST /admin/orders/pricing-context/quote` (không giữ tồn) → `POST /admin/orders`. Chỉ COD, phí ship 0. Cùng công thức đặt hàng storefront (giá hiệu lực − khuyến mãi sản phẩm − voucher; chỉ 1 voucher, mã thừa trả `REJECTED / ONLY_ONE_VOUCHER_ALLOWED`). `pricingEvaluationId` là băm của (SKU, số lượng, giá, giảm, voucher, tổng); `pricingEvaluationId` hoặc `expectedGrandTotalMinor` lệch → 409 `ORDER_PRICING_CHANGED` (không giữ tồn). `idempotencyKey` dùng cùng cơ chế Idempotency-Key (owner `A:<adminId>`, 24h).
- **Sửa đơn** `PUT /admin/orders/{id|mã}`: `PENDING/CONFIRMED`; đổi dòng hàng chỉ `PENDING` (nhả tồn cũ, giữ chỗ mới, voucher được nhả rồi kiểm tra lại trên giá mới). **Nhân bản** `POST .../duplicate`. **Xác nhận thanh toán** `PUT .../confirm-payment`.
- **Ghi chú** append-only, `type` `order|internal`; **audit log** `entityName` `oms|order_refund`, mỗi dòng có actor (id, tên, vai trò) và `changes[]` theo trường. Luồng cũ (PATCH status, cancel, return, refund) cũng ghi audit.
- **Đổi/trả**: xem `FE-NOTE-CON-LAI.md` A2. Số tiền trả = giá dòng × (đã thanh toán / tạm tính) làm tròn đồng; `priceDifference = exchangeTotal − returnTotal`. Mã `RT<millis><3 số>`. Duyệt chạy trong một transaction (lỗi giữa chừng → rollback toàn bộ, kể cả nhập kho/hoàn tiền).
- Mã lỗi mới: `ORDER_PRICING_CHANGED`, `ORDER_RETURN_QUOTE_STALE`; còn lại dùng `VALIDATION_FAILED`, `INVALID_STATUS_TRANSITION`.
- Không làm: xoá đơn, hoá đơn VAT, thanh toán online, GHN/3PL, kho, lấy/kiểm hàng đổi trả, `type=Compensation`.
- Test: `OmsAdvancedIT` (3 test: tạo/báo giá/sửa/ghi chú/thanh toán/nhân bản/audit; đổi trả Refund + Exchange + Reject; phân quyền).

### 7.4 Wizard chiến dịch khuyến mãi (V33)
- Bảng: `promo_drafts`, `promo_draft_components` (TARGET/AUDIENCE/APPLICABILITY), `promo_draft_schemas`, `promo_schema_components` (CONDITION/BENEFIT), `promo_voucher_codes`, `promo_published`.
- Luồng: tạo nháp → target/audience/applicability → thêm schema (MARKDOWN/FLAT_PRICE/VOUCHER) → điều kiện + lợi ích (+ mã voucher) → `READY_TO_PUBLISH` (tính từ dữ liệu) → publish → vòng đời `ACTIVE/PAUSED/ENDED/ARCHIVED` (+ `SCHEDULED/ENDED` tính theo giờ). Publish hiện thực hoá MARKDOWN/FLAT_PRICE thành `promotions` (+ `promotion_products`), VOUCHER thành `vouchers` để engine giá/checkout hiện có dùng ngay.
- Authoring contract V2: `condition-config-options` / `benefit-config-options` trả định nghĩa form (`authoringContractVersion 2.0.0`, `configType`, `configVersion 1.0.0`); PUT body `{configType, configVersion, values}`; BE lưu cả `config` chuẩn hoá lẫn `authoringValues`. Mã voucher: 3–40 ký tự `[A-Z0-9_-]`, trùng → 409 `DUPLICATE_CODE`; chính sách hiển thị `PUBLIC|PRIVATE` (PRIVATE cần kênh ZNS/EMAIL/OTHER).
- Runtime: `runtime/evaluate` (cùng công thức engine giá), `drafts/{id}/simulate` (published + draft, trả conflict `DRAFT_REPLACES_PUBLISHED`…), discover/validate voucher (lý do `VOUCHER_VALID`, `VOUCHER_NOT_FOUND`, `VOUCHER_MIN_ORDER_NOT_MET`…), tra khách theo SĐT.
- Giới hạn: xem `FE-NOTE-CON-LAI.md` A4.
