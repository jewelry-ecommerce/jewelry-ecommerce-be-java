# Thay đổi BE theo yêu cầu FE audit (2026-10-28)

Chỉ sửa logic kiểm tra và dữ liệu/response trả ra; **không thêm bảng, không thêm migration, không đổi cấu trúc module**.
Chưa commit. Chưa chạy `mvn` (xem mục "Đã kiểm chứng" cuối file).

## 1. SEC-01 — COD và callback thanh toán
- Storefront đặt đơn (đăng nhập và khách vãng lai) nay từ chối phương thức khác COD: **400 `PAYMENT_METHOD_UNSUPPORTED`**
  ("Hiện chỉ hỗ trợ thanh toán COD"). Admin tạo/sửa đơn cũng dùng mã này (trước đây là `VALIDATION_FAILED`).
- `POST /storefront/payment/{paymentId}/callback`: bỏ secret mặc định `dev-only-insecure-secret`. `app.payment.webhook-secret`
  để trống (mặc định) thì callback bị từ chối **503 `PAYMENT_NOT_CONFIGURED`** trước khi đọc DB (fail-closed).
  Chỉ khi chọn cổng thanh toán thật và đặt secret mới dùng được (xem cảnh báo trong `PaymentServiceImpl`).

## 2. CMS-02 — validate config block
Admin tạo/sửa block (và tạo/sửa trang kèm blocks) nay kiểm theo schema FE hiện gửi cho: `INFO_CARDS`, `IMAGE_GALLERY`,
`PRODUCT_LIST`, `PRODUCT_CATEGORY_NAV`, `PRODUCT_COLLECTION_SHOWCASE`, `PRODUCT_EXPANDABLE_DESCRIPTION`
(`BANNER`, `PRODUCT_CAROUSEL` giữ nguyên). Field lạ **được giữ nguyên**, không bị từ chối hay xoá; giá trị `null` ở field tuỳ chọn coi như không gửi.

Lỗi trả **400 `CMS_BLOCK_CONFIG_INVALID`**, `errors[0].field` là đường dẫn field (vd `config.items[0].imageUrl`),
`message` có kèm mô tả. Các luật:

| Block | Luật |
|---|---|
| INFO_CARDS | `header.title` bắt buộc; `items` ≥ 1 phần tử, mỗi phần tử bắt buộc `imageUrl`, `title`, `actionText`, `actionUrl`; `display.columns` ∈ {2,3,4} |
| IMAGE_GALLERY | `header.title`, `items` ≥ 1 (mỗi phần tử bắt buộc `imageUrl`); `display.layoutStyle` ∈ GRID/CAROUSEL; `actionType` ∈ LINK_URL/MIX_MATCH; `mixMatchSkus[].variationId` bắt buộc |
| PRODUCT_LIST | `productDataSource.filterType` ∈ ALL_PRODUCTS/COLLECTION/PROMOTION (bắt buộc); `sort` ∈ best_selling/price_asc/price_desc/newest/name/name_desc; COLLECTION cần `collectionSlugs` không rỗng; `dataSource.filterType` = CMS_STOREFRONT |
| PRODUCT_CATEGORY_NAV | `display.limit` nguyên ≥ 0 |
| PRODUCT_COLLECTION_SHOWCASE | `header.title`; `items` 1–3 phần tử, mỗi phần tử bắt buộc `title`, `imageUrl` |
| PRODUCT_EXPANDABLE_DESCRIPTION | `title`, `description` bắt buộc; `maxCollapsedLines` nguyên ≥ 1 |

URL (`imageUrl`, `actionUrl`): chỉ nhận `http(s)://…` hoặc đường dẫn tương đối bắt đầu bằng `/`; link điều hướng còn nhận `#neo`, `mailto:`, `tel:`.
Từ chối `javascript:`, `data:`, `vbscript:`, `file:`, `//host`, khoảng trắng/ký tự điều khiển, `< > " ' \`, dài > 2048 ký tự.
Chỉ kiểm lúc ghi; block đã lưu từ trước không bị đụng, publish không kiểm lại.

Để trả được `errors[].field` cho mọi lỗi nghiệp vụ, `BusinessException` có thêm `withField(...)`; các lỗi cũ không đổi.

## 3. INV-01 — điều chỉnh kho `POST /admin/inventory/adjustments`
- `quantityDelta = 0` → **400 `STOCK_ADJUSTMENT_INVALID`** (`errors[0].field = quantityDelta`).
- Chính sách dấu: **RECEIPT chỉ dương, DAMAGE chỉ âm, CORRECTION tăng hoặc giảm (khác 0)**. Sai dấu → 400 `STOCK_ADJUSTMENT_INVALID`.
- 422 `NEGATIVE_STOCK`: tồn sau điều chỉnh < 0. 422 `STOCK_OVERFLOW`: vượt giới hạn INT (tính bằng long/BIGINT nên không còn lỗi tràn ở SQL).
- 422 `STOCK_BELOW_RESERVED`: giảm tồn xuống dưới phần đang giữ chỗ cho đơn (hàng thường không bị biến thành backorder vì điều chỉnh giảm);
  SKU đang nợ (reserved > on_hand) không giảm thêm được nhưng vẫn nhập bù (RECEIPT) bình thường.
- 409 `STOCK_CONFLICT` khi thua race (tồn vừa đổi, vẫn hợp lệ → thử lại).
- Không đụng `reserved`, không thêm constraint toàn cục (giữ pre-order V11). `ProductSkuRepo.adjustOnHand` (dùng cho trả hàng) giữ nguyên;
  form admin dùng câu SQL mới `ProductSkuRepo_adjustOnHandChecked.sql`.
- Sổ kho + idempotency: xem mục 3b.

## 3b. INV-02 — sổ kho (migration V37)
**Phạm vi:** chỉ ghi các lần **điều chỉnh thủ công** của admin. Biến động do đơn hàng (giữ chỗ, giao, trả) **chưa** vào sổ; không backfill dữ liệu cũ.
**DB:** bảng mới `inventory_movements` (V37, chỉ thêm bảng, không đụng bảng cũ): `sku_id, warehouse_id('MAIN'), reason, quantity_delta, on_hand_before, on_hand_after, reserved_after, reference, note, idem_key, request_hash, created_id, created_date`.
CHECK: `on_hand_after = on_hand_before + quantity_delta`, `quantity_delta <> 0`, reason ∈ RECEIPT/DAMAGE/CORRECTION. UNIQUE `(created_id, idem_key)`.
Dòng sổ được ghi **cùng transaction** với việc đổi `product_skus.on_hand` → hoặc cả hai thành công, hoặc không có gì.

**POST `/admin/inventory/adjustments`** — body thêm hai trường tuỳ chọn `reference` (≤100 ký tự, vd số phiếu nhập) và `note` (≤500). Header tuỳ chọn `Idempotency-Key` (`[A-Za-z0-9._:-]{8,100}`):
- Không gửi header → hành xử như cũ (mỗi lần gọi cộng một lần), vẫn có dòng sổ.
- Gửi lại **cùng key + cùng nội dung** → 201 trả tồn hiện tại, **không cộng lần hai**.
- **Cùng key, khác nội dung** → 409 `IDEMPOTENCY_KEY_REUSED`. Key sai định dạng → 400 `IDEMPOTENCY_KEY_INVALID`.
- Hai request cùng key chạy đồng thời: một cái thành công, cái kia 409 `STOCK_CONFLICT` (đã rollback, gửi lại sẽ nhận bản replay).
- Key là của từng admin và không hết hạn.

**GET `/admin/inventory/movements`** — lọc `skuId`, `reason`, `from`, `to` (ISO-8601, ngày thuần `to` tính trọn ngày), phân trang `page` (từ 1) / `take` (mặc định 20, tối đa 100). Mới nhất trước. Trả `{content,page,size,totalElements,totalPages}`.
`reason` hoặc ngày sai → 400 `VALIDATION_FAILED` (có `errors[0].field`).
**GET `/admin/inventory/movements/{id}`** — 404 nếu không có.
Mỗi dòng: `id, skuId, skuCode, skuName, warehouseId, reason, quantityDelta, onHandBefore, onHandAfter, reservedAfter, reference, note, actorId` (id admin thực hiện), `createdDate` (không lộ Idempotency-Key).
Cả ba endpoint thuộc nhóm `/admin/**` (cần quyền ADMIN như các API kho hiện có).

**Cần làm khi triển khai:** DB dev tắt Flyway → chạy lại `reset-db.bat` (hoặc chạy tay `V37__inventory_movements.sql` và `V38__search_keywords.sql`) để có bảng mới. DB cloud (Neon) tự nhận V37, V38 khi BE deploy lại trên Render.
**FE (INV-03):** thêm màn lịch sử, nên gửi `Idempotency-Key` (UUID mới mỗi lần bấm lưu, giữ nguyên khi tự gửi lại) và dùng `reference`/`note`.

## 4. Yêu cầu khách (newsletter, hàng về lại, hỗ trợ đơn)
Response thêm hai cờ (vẫn có `success`, `message`): `recorded: true` (đã lưu yêu cầu) và `notificationSent: false` (chưa có thông báo thật gửi đi — hệ thống chưa có bộ gửi SMS/email).
Câu thông báo không còn hứa hẹn đã gửi. FE nên dùng hai cờ này thay vì suy từ `message`.

## 4b. Kho file chung — ảnh, video, SVG, CSV (không đổi cấu trúc response)
**Một thư mục gốc `UPLOAD_DIR`, tách folder theo mục đích** (`UploadStorage`):

| Folder | Nội dung | Truy cập |
|---|---|---|
| `images/yyyy/mm/` | png, jpg, gif, webp, **svg tĩnh** | công khai `GET /storefront/files/images/...` |
| `videos/yyyy/mm/` | mp4, webm | công khai `GET /storefront/files/videos/...` |
| `private/import-export/yyyy/mm/` | CSV import/export | **không bao giờ công khai**, chỉ tải bằng link ký có hạn |
| `yyyy/mm/` (cũ) | ảnh/video đã upload trước đây | vẫn phục vụ như cũ, URL đã lưu trong DB không đổi |
| `ie/` (cũ) | CSV cũ | bị chặn công khai (404), vẫn tải được qua link ký |

- **CSV riêng tư:** `fileUrl` trong job import/export giờ là link ký có hạn, vd `/api/v1/storefront/private-files/import-export/12?exp=1767000000&sig=...` (HMAC-SHA256, mặc định hiệu lực 10 phút). Mở lại job (`GET /admin/ie/job/status/{id}` hoặc danh sách job) để lấy link mới. FE vẫn dùng `fileUrl` như cũ, không cần đổi.
  Link sai/hết hạn/job khác → 403 `FILE_LINK_INVALID`. File không còn trên máy chủ → 404 `FILE_NOT_AVAILABLE` ("hãy xuất lại"). Chặn truy cập qua `/files/ie/...`, `/files/private/...` kể cả viết hoa hay `%`-encode.
- **SVG tĩnh an toàn:** nhận `.svg` (≤ 512 KB) qua cùng API upload ảnh. Từ chối (400 `FILE_REJECTED`, có lý do): DOCTYPE/entity (XXE), `<script>`, `foreignObject`, `iframe/object/embed`, `animate*/set`, thuộc tính `on*`, `href` không phải `#id`, `javascript:`/`data:`, CSS `@import`/`url()` ra ngoài, processing instruction.
  Khi phục vụ file `.svg` còn kèm `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox` và `X-Content-Type-Options: nosniff`.
- **Biến môi trường:** `UPLOAD_DIR` (gốc lưu file), `UPLOAD_PUBLIC_BASE_URL` (như cũ), `UPLOAD_SIGNING_SECRET` (khoá ký link CSV; để trống = khoá ngẫu nhiên mỗi lần khởi động, link cũ hết hiệu lực khi restart; **không đặt giá trị thật vào git**), `UPLOAD_LINK_TTL_SECONDS` (60–86400, mặc định 600).
- **Giới hạn thật cần biết:** nếu `UPLOAD_DIR` nằm trên ổ tạm của Render thì mọi file (kể cả ảnh) mất khi máy dựng lại. Muốn bền phải gắn ổ đĩa bền (persistent disk) rồi trỏ `UPLOAD_DIR` vào đó. CSV mất thì chỉ cần xuất lại.
  Chưa có Cloudinary trong repo BE này (mã Cloudinary của bản BE trong audit không nằm ở đây).

## 4c. Search: trending, gợi ý, lịch sử (migration V38)
**DB:** hai bảng mới, chỉ thêm bảng. `search_keywords` (từ khoá + số lượt + lần tìm gần nhất, duy nhất theo dạng bỏ dấu nên "nhẫn" và "nhan" gộp một) và `search_history` (mỗi tài khoản tối đa 20 từ khoá gần nhất).
**Ghi nhận:** khi `GET /storefront/product?search=...` trả **trang 1 có kết quả**, từ khoá được ghi (khách vãng lai chỉ góp vào trending/gợi ý; đã đăng nhập thì thêm vào lịch sử cá nhân). Tìm không ra kết quả hoặc trang ≥ 2 thì không ghi. Lỗi ghi nhận không bao giờ làm hỏng kết quả tìm kiếm. Không có dữ liệu giả: chưa ai tìm thì trending rỗng.

| API | Quyền | Trả về |
|---|---|---|
| `GET /storefront/product/search/trending?limit` | công khai | `["charm","nhẫn",...]` (30 ngày gần nhất, limit mặc định 10, tối đa 20) |
| `GET /storefront/product/search/suggestions?q` | công khai | `[{text,type:"category"\|"keyword",slug?}]` — tối đa 3 danh mục (kèm `slug`) rồi từ khoá phổ biến, tổng ≤ 8, không phân biệt dấu; `q` < 2 ký tự → `[]` |
| `GET /storefront/product/search/history?limit` | đăng nhập | `[{keyword,searchedAt}]` mới nhất trước |
| `DELETE /storefront/product/search/history` | đăng nhập | xoá toàn bộ |
| `DELETE /storefront/product/search/history/keyword/{keyword}` hoặc `.../history/keyword?keyword=...` | đăng nhập | xoá một từ khoá (không phân biệt hoa thường/dấu); không có thì bỏ qua |

**FE cần nối (BE chưa sửa FE):** trong `java-backend.config.ts` thêm alias `catalog/search/trending → storefront/product/search/trending`, `catalog/search/suggestions → storefront/product/search/suggestions`, `catalog/search/history → storefront/product/search/history`, `catalog/search/history/keyword → storefront/product/search/history/keyword`.
Trong `product.api.ts`: `getSearchSuggestions` đang ghép `?q=` vào path (không map được và không encode) → đổi sang `params: { q }`; `deleteSearchHistoryKeyword` đổi sang `authAxios.delete('catalog/search/history/keyword', { params: { keyword } })` vì BFF chỉ cho ký tự ASCII trong path.

## 5. Chưa làm trong đợt này
- PIM-02 catalog chậm: cần đo trên DB cô lập rồi mới gom query theo lô (không refactor khi chưa đo được).
- OTP sender thật: để sau theo chỉ định của chủ dự án (cần chọn kênh).
- Bỏ qua theo chỉ định: đối chiếu BE HEAD `5b695f5`.
- FE nối search (xem 4c) và hiển thị link CSV/ảnh theo cấu trúc mới.

## 6. Đã kiểm chứng / chưa
- Đã chạy thật (javac + java, không qua Maven): bộ kiểm tra CMS (11 ca), luật dấu/tràn/giữ chỗ của kho (14 ca), câu SQL kho mới trên PostgreSQL 16 (9 ca), V37 + 3 câu truy vấn sổ kho trên PostgreSQL 16 (unique, CHECK, lọc, phân trang), hàm phụ sổ kho (14 ca), chính sách SVG (29 ca), quy tắc public/private của kho file (20 ca), ký/kiểm link tải (10 ca), chuẩn hoá từ khoá (14 ca), V38 và các câu truy vấn search (bump, rename, trending, gợi ý, lịch sử, cắt 20, xoá) trên PostgreSQL 16.
- **Chưa chạy `mvn compile/test`** (môi trường của mình không tải được thư viện Maven). Các test mới/sửa chưa chạy:
  `CmsBlockConfigValidatorTest`, `InventoryAdjustServiceTest`, `InventoryLedgerIT`, `SvgSafetyTest`, `SearchTextTest`, `SearchKeywordServiceTest`, `SearchHistoryIT`, `PrivateFilesIT`, `AdminUploadIT` (thêm ca mới), `ImportExportIT`, `CustomerRequestServiceTest`, `CmsBannerControllerIT`, `CmsBlocksControllerIT`, `CmsPublishV5IT`.
  Cần chạy `mvn clean install` trên máy bạn.
