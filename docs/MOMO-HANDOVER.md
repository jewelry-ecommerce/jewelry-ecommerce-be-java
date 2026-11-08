# Bàn giao MoMo Sandbox sang máy cá nhân — 08/11/2026

## Đọc trước

Đây là đồ án jewelry e-commerce. Mục tiêu là demo thanh toán MoMo Sandbox, không thu tiền thật. Code BE + client + admin đã được chuẩn bị; chưa có bộ key Test của chủ tài khoản nên chưa xác nhận giao dịch với MoMo thật trên Sandbox. Không được báo đã tích hợp online thành công chỉ vì build/test local qua.

Email MoMo ngày 02/11 trong ảnh là hướng dẫn hai cách đăng ký, chưa phải email cấp API key. Chọn **Tích hợp API trực tiếp** cho code hiện tại. Luồng qua Nhanh.vn tạo QR/link là một phương án khác, không thay thế bộ key của tích hợp này.

## Lấy code trên laptop

Ba repo đều dùng nhánh bàn giao `feat/momo-sandbox-handover`:

| Thư mục | Git URL | Nhánh đích PR |
|---|---|---|
| jewelry-commerce-admin | https://github.com/jewelry-ecommerce/jewelry-ecommerce-admin.git | develop |
| jewelry-commerce-client | https://github.com/jewelry-ecommerce/jewelry-ecommerce-client.git | develop |
| jewelry-ecommerce-be-java | https://github.com/jewelry-ecommerce/jewelry-ecommerce-be-java.git | main |

Clone nếu chưa có: `git clone --branch feat/momo-sandbox-handover <URL> <thu-muc>`.
Nếu đã có repo: kiểm tra `git status`, giữ thay đổi đang làm, rồi `git fetch origin` và `git switch --track origin/feat/momo-sandbox-handover` nếu chưa có nhánh local. Nếu có rồi thì switch nhánh và `git pull --ff-only`.

Đọc AGENTS.md/README của workspace thực tế trước khi sửa. Không giả định laptop dùng ổ J. Không dùng lại repo partner kltn_ecommerce để triển khai; đó là nguồn v8 đã được hợp nhất vào repo BE riêng. Không reset DB, chạy seed lại hoặc làm mất 100 sản phẩm/CMS đang có.

Nhánh bàn giao chưa được merge/deploy. Quy tắc workspace cấm push trực tiếp develop/staging/test; đưa vào develop qua PR đã review. Lệnh push không tự cho phép merge PR hay deploy. Chỉ thực hiện các bước đó sau khi người dùng cho phép trên máy cá nhân.

## Đăng ký MoMo để lấy key

1. Mở https://business.momo.vn/ và đăng ký/đăng nhập tài khoản của chủ đồ án; dùng thông tin thật. Không cần đưa mật khẩu cho agent.
2. Trong email MoMo, chọn cột **Tích hợp API trực tiếp**, bấm **Điền form đăng ký hợp tác** nếu portal yêu cầu tiếp tục hồ sơ. Không chọn Nhanh.vn cho mục tiêu nối API hiện tại.
3. Khai website bán hàng: https://jewelry-ecommerce-client.vercel.app/ . Mô tả nhu cầu: đồ án học tập, cần môi trường Test/Sandbox cho thanh toán ví một lần (captureWallet), không thu tiền thật.
4. Sau khi đăng ký thành công, tìm phần thông tin tích hợp/API trên portal và chọn môi trường **Test**. Lấy đủ `Partner Code`, `Access Key`, `Secret Key` cùng môi trường. Tên/vị trí menu có thể thay đổi; không đoán key hoặc dùng key ví dụ trong tài liệu.
5. Nếu portal chỉ hiện yêu cầu hồ sơ/hợp đồng hoặc chưa thấy key, liên hệ lại bộ phận MoMo trong email, xin hướng dẫn cấp thông tin Sandbox cho đồ án. Việc nhận email hướng dẫn không đồng nghĩa đã được cấp key. Chưa có key thì giữ MoMo tắt, COD vẫn dùng.
6. Làm theo hướng dẫn MoMo Test để cài ứng dụng/tài khoản ví Test nếu kịch bản yêu cầu. Không quét QR thử nghiệm bằng ví thật để thử chuyển tiền.

Tài liệu chính thức: [Đăng ký merchant](https://developers.momo.vn/v3/docs/payment/onboarding/merchant-profile/), [Quy trình tích hợp và key Test/Production](https://developers.momo.vn/v3/docs/payment/onboarding/integration-process/), [Hướng dẫn test](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/).

Không cam kết miễn phí thanh toán thương mại thật: bản này dùng Sandbox để thử nghiệm. Nếu được gửi key Production, không cắm vào code Sandbox hoặc đổi gateway sang live tùy tiện.

## Cấu hình khi đã có key Test

Trên Render, chọn service jewelry-ecommerce-be-java → Environment. Giữ nguyên toàn bộ DB/JWT/CORS/Cloudinary hiện có, chỉ thêm/cập nhật:

```dotenv
MOMO_ENABLED=true
MOMO_PARTNER_CODE=<Partner Code Test>
MOMO_ACCESS_KEY=<Access Key Test>
MOMO_SECRET_KEY=<Secret Key Test>
MOMO_REDIRECT_URL=https://jewelry-ecommerce-client.vercel.app/thanh-toan-momo
MOMO_IPN_URL=https://jewelry-ecommerce-be-java.onrender.com/api/v1/storefront/payment/momo/ipn
MOMO_TIMEOUT=30s
```

Các giá trị trong dấu <> là chỗ điền, không phải key thật. Secret chỉ lưu Render Environment hoặc file local bị gitignore, không commit và không gửi vào chat/log. Không đưa vào VITE_* hay NEXT_PUBLIC_*; không có key MoMo nào cần ở FE. Không thay JWT key bằng MoMo key. Public Key RSA của MoMo không cần cho luồng captureWallet HMAC đang triển khai.

Sau khi được phép release, deploy BE trước để Flyway áp dụng `V39__momo_sandbox_payment.sql` (thêm gateway_order_id, checkout_url và unique index; không reset dữ liệu), sau đó deploy client/admin đúng commit đã review. Không sửa migration cũ đã chạy. Nếu DB mới phát sinh migration cùng số V39, phải xử lý xung đột trước deploy.

Client tự gọi `GET /api/v1/storefront/payment/methods`; chỉ hiện MoMo khi BE trả `momoEnabled=true`. Nếu chưa có key, giữ `MOMO_ENABLED=false`.

Giữ cấu hình cloud FE đang chạy, tránh localhost:

```dotenv
# Client Vercel
NEXT_PUBLIC_BACKEND_PROVIDER=JAVA
API_BE_URL=https://jewelry-ecommerce-be-java.onrender.com
API_BE_PREFIX=/api/v1
NEXT_PUBLIC_CONTENT_API_URL=https://jewelry-ecommerce-be-java.onrender.com
NEXT_PUBLIC_CONTENT_API_PREFIX=/api/v1
NEXT_PUBLIC_SITE_URL=https://jewelry-ecommerce-client.vercel.app
AUTH_COOKIE_SECURE=true
```

```dotenv
# Admin Vercel
VITE_DATA_SOURCE=HTTP
VITE_BACKEND_PROVIDER=JAVA
VITE_API_BASE_URL=https://jewelry-ecommerce-be-java.onrender.com
VITE_API_PREFIX=/api/v1
```

Thay ENV FE phải redeploy mới có hiệu lực. Render Free có thể ngủ; mở `/actuator/health` và chờ UP trước demo. IPN chậm không được xử lý bằng cách tin kết quả redirect hoặc cho admin tự đánh dấu đã trả tiền.

## Code đã làm

- BE: cấu hình MoMo tắt mặc định; tạo link Sandbox; xác thực IPN HMAC-SHA256/merchant/mã giao dịch/số tiền/đơn; lưu gateway reference, tái sử dụng link cùng đơn, chống callback trùng; giữ COD.
- Client: chọn MoMo khi backend sẵn sàng, giữ phương thức qua quote/create, lưu mã đơn trước khi chuyển trang, trang `/thanh-toan-momo` mở link và đọc trạng thái BE. Không tin `resultCode` trên URL để xác nhận trả tiền.
- Admin: map MoMo trong OMS, chặn sửa/xác nhận thanh toán tay cho đơn MoMo và chặn giao đơn MoMo chưa PAID.
- Kho có một MAIN: SKU có sẵn trong màn tồn kho; nhập/điều chỉnh thủ công; tạo đơn giữ tồn, hủy nhả tồn, DELIVERED trừ onHand/reserved. Không làm Redis hoặc tối ưu catalog ở đợt này; thay đổi tối ưu trước đã revert theo yêu cầu.

API mới dưới `/api/v1/storefront/payment`:
- `GET /methods`: `{data: {momoEnabled, sandbox: true}}`.
- `POST /momo/start`: body `{orderCode, phone?}`; member phải sở hữu đơn, guest phải có mã đơn + SĐT. Trả link Sandbox của đúng đơn.
- `POST /momo/ipn`: MoMo gửi notification có chữ ký; xử lý thành công HTTP 204.

BE nguồn chính: `pk-core/pk-business/src/main/java/com/pk/core/business/payment/`, `pk-api/.../MomoRest.java`, `pk-model/.../PaymentEntity.java`, migration V39. Client nguồn chính: `src/utils/api/java/java-momo.api.ts`, `java-checkout.api.ts`, `src/app/(layout-focus)/thanh-toan-momo/page.tsx`. Admin: order-java.adapter và phần action của chi tiết đơn.

## Luồng và kiểm tra ngắn khi có key

1. Đơn MoMo mới: `PENDING / UNPAID`, admin đã thấy đơn, stock đang giữ. Chuyển tới trang thanh toán Sandbox.
2. Thành công ở MoMo Test → IPN hợp lệ resultCode 0 → payment SUCCESS, order paymentStatus PAID; orderStatus vẫn PENDING. Client xem kết quả/lịch sử; admin xử lý xác nhận → giao → DELIVERED.
3. Hủy/thất bại: không tự PAID, không giao đơn chưa trả tiền. Nếu callback đến trễ sau hủy: giữ CANCELLED, ghi PAID nếu tiền thực sự được báo thành công, thêm ghi chú cần kiểm tra hoàn tiền; không mở lại đơn/trừ tồn thêm.
4. Kiểm tra một đơn COD để bảo đảm luồng cũ vẫn dùng; refresh/trở lại trang thanh toán không tạo thêm đơn. Kiểm tra guest và member nếu dùng cả hai trong demo.
5. Nếu không thấy MoMo: xem `/methods`, đúng ENV/đúng deployment, không cần tạo lại sản phẩm/DB. Nếu đã chuyển MoMo mà vẫn UNPAID: xem IPN/log Render đã che key và trạng thái phía MoMo; không giả lập PAID bằng SQL.

Giới hạn hiện tại: chỉ Sandbox, 1.000–50.000.000 VND nguyên đồng; một phiên thanh toán mỗi đơn; chưa có retry phiên mới sau thất bại/hết hạn, query đối soát hoặc hoàn tiền MoMo tự động. Nếu chỉ lỗi mở link, thử lại cùng đơn; nếu giao dịch đã thất bại/hết hạn, kiểm tra trạng thái rồi hủy/đặt lại. Guest nên dùng cùng trình duyệt/tab giữ thông tin tra cứu.

## Kiểm tra source và phần chưa xác minh

Đợt bàn giao chạy build production admin/client và Maven verify với H2 riêng, tập trung MoMo/COD/idempotency/order lifecycle; không dùng DB Neon để chạy test. Kết quả ngày 08/11/2026: build admin/client thành công; Java Maven verify thành công, 15 test qua (MoMo/COD/idempotency/lifecycle); client 21 test qua; admin 7 test qua. Các build có warning sẵn có nhưng không có lỗi chặn build.

Chưa có key nên chưa thực hiện thanh toán Test thật với portal/ví MoMo, chưa xác nhận IPN qua Render, chưa áp V39 lên Neon và chưa release ba nhánh này. Không bảo đảm chỉ điền key là chắc chắn chạy trên cloud: agent cần kiểm tra thực tế URL/IPN/quyền merchant sau khi có key. Nếu không phát sinh lỗi thực tế, không viết lại luồng đang có.

## Prompt cho agent Codex trên laptop

> Tôi chuyển từ máy công ty sang laptop. Hãy đọc `docs/MOMO-HANDOVER.md` trong cả 3 repo; bản giống nhau và chứa context, cấu hình, giới hạn, link hướng dẫn đăng ký. Repo admin/client của jewelry-ecommerce dùng develop làm nhánh đích, BE jewelry-ecommerce-be-java dùng main; code bàn giao nằm ở feat/momo-sandbox-handover. Giữ thay đổi local, fetch và kiểm tra đúng commit/PR, không dùng repo partner kltn_ecommerce để deploy. Mục tiêu là hoàn tất demo MoMo Sandbox, không thu tiền thật. Hãy hướng dẫn tôi đăng ký MoMo Business qua API trực tiếp để lấy key Test; không yêu cầu gửi secret vào chat. Khi tôi có key, hướng dẫn nhập trực tiếp Render Environment theo file, giữ nguyên DB/JWT/CORS/Cloudinary. Rà code có sẵn trước, chỉ sửa lỗi thực tế cần thiết; không thêm Redis/tối ưu catalog hoặc đổi scope. Trước merge/deploy hãy trình bày đúng 3 PR/commit và xin phép release nếu phiên này chưa được phép; không push trực tiếp develop. Sau khi được phép, release BE trước (Flyway V39, không reset/seed DB), rồi client/admin. Kiểm tra ngắn checkout MoMo → IPN → PAID → OMS → lịch sử đơn, hủy/thất bại và COD; không chạy bộ kiểm thử dài không cần thiết. Chỉ IPN có chữ ký hợp lệ mới cập nhật PAID, tuyệt đối không tin query redirect hay SQL đánh dấu PAID. Báo rõ việc đã hoàn thành và những gì chưa thể xác minh nếu chưa có key/quyền merchant. Nếu email chỉ là hướng dẫn đăng ký thì nói rõ chưa có API key, không giả định đã được cấp.
