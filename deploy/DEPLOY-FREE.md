# Đưa đồ án lên cloud miễn phí

## 0. Đã chuẩn bị và phần bạn phải đăng nhập

Source đã có Docker, CI ba repo, profile cloud, provider Cloudinary và file env mẫu. **Chưa tạo tài khoản/tài nguyên cloud, chưa có domain backend thật và chưa push code.**

Bạn dùng **tieuphu142003@gmail.com** khi đăng ký Neon, Cloudinary và Render. Agent không được dùng tài khoản Google khác đang kết nối với phiên làm việc. Không gửi API secret, DB password, khóa JWT vào chat.

Free có hạn mức, không có SLA. Render ngủ khi không có traffic và Java cần khởi động lại. Không dùng dịch vụ trả phí, trial tự nâng cấp hay thêm thông tin thanh toán nếu không cần.

## 1. Neon — PostgreSQL riêng cho cloud

1. Mở https://console.neon.tech và đăng ký/đăng nhập.
2. Chọn gói Free, tạo project riêng `jewelry-ecommerce`; chọn vùng gần Render nếu khả dụng.
3. Trong Connect, lấy thông tin **direct connection**, không chọn hostname có `-pooler` cho bước triển khai Flyway này.
4. Tạo ba biến môi trường trong Render:
   - `DB_URL=jdbc:postgresql://<direct-host>/<database>?sslmode=require`
   - `DB_USERNAME`: user Neon.
   - `DB_PASSWORD`: password Neon.
5. DB này tách khỏi máy local/công ty. Không đưa nguyên URL `postgresql://user:password@host` vào `DB_URL`; Java dùng JDBC URL, user/password khai báo riêng.

Mỗi lần app khởi động, Flyway chỉ áp migration chưa chạy. Không tắt Flyway, không tự baseline DB đã có bảng và không chạy script reset.

## 2. Cloudinary — lưu ảnh/video

1. Mở https://cloudinary.com/pricing.
2. Chọn **Image and Video APIs → Free**, không chọn DAM trial 30 ngày.
3. Trong product environment/dashboard, lấy Cloud name, API key, API secret.
4. Nhập vào Render: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.
5. `CLOUDINARY_FOLDER=jewelry-commerce`.

Admin vẫn gọi API upload Java như cũ. Java kiểm tra nội dung, ký và gửi file; FE chỉ nhận URL HTTPS. Không tạo unsigned upload preset công khai, không đặt secret trong `VITE_*` / `NEXT_PUBLIC_*`.

Ảnh upload mới được lưu trên Cloudinary. **Ảnh cũ đang dùng URL localhost hoặc file local không tự chuyển**; phải upload lại/chuyển dữ liệu có kiểm soát. Không đổi hàng loạt URL trong DB nếu chưa có bản sao lưu và đối chiếu ảnh.

Giới hạn còn lại: file CSV import/export vẫn là file tạm trên backend, có thể mất khi Render ngủ/restart. Tải kết quả ngay; chưa phải kho lưu trữ xuất báo cáo lâu dài. Không upload CSV chứa dữ liệu khách lên Cloudinary công khai.

## 3. Khóa JWT cố định

Ở gốc repo, chạy:

```sh
node scripts/generate-jwt-keys.mjs
```

Script tạo `deploy/secrets/jwt-private.pem` và `jwt-public.pem`, đã được gitignore. Không ghi nội dung khóa vào log/chat, không tạo lại khóa mỗi lần deploy.

Dán nguyên nội dung PEM vào hai biến multiline của Render: `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`. Giữ bản sao an toàn để đổi máy. Nếu đổi private key, access token cũ sẽ không còn hợp lệ.

## 4. Render — backend và URL HTTPS

Sau khi chủ dự án duyệt và push thay đổi lên repo:

1. Đăng nhập https://dashboard.render.com bằng đúng tài khoản, chọn New → Blueprint.
2. Kết nối GitHub và chỉ cấp quyền repo `jewelry-ecommerce/jewelry-ecommerce-be-java`.
3. Chọn branch `main`, Render đọc `render.yaml`.
4. Kiểm tra service **Free**. Blueprint không yêu cầu DB/disk Render; nếu UI hiện phí thì dừng, không xác nhận thanh toán.
5. Nhập các biến còn thiếu theo `.env.cloud.example`.
6. `SPRING_PROFILES_ACTIVE=cloud`; không dùng `dev`.
7. `CORS_ALLOWED_ORIGINS` là đúng hai origin HTTPS Admin/Client, phân cách bằng dấu phẩy, không có dấu / cuối, không dùng wildcard.
8. Backend tự dùng `RENDER_EXTERNAL_URL` do Render cấp để tạo link tải CSV. Chỉ đặt `UPLOAD_PUBLIC_BASE_URL` nếu cần ghi đè bằng origin HTTPS khác, không có `/api/v1`; không cần đoán URL trước khi tạo service.
9. Chờ GitHub Java CI pass và Render deploy. `autoDeployTrigger: checksPass` cần GitHub integration; dùng On Commit nếu chưa có checks chỉ để xử lý lần đầu, sau đó bật lại After CI Checks Pass.
10. Mở `https://<service-thực-tế>.onrender.com/actuator/health`, mong đợi HTTP 200 và `status=UP`.

Tên URL Render chỉ là ví dụ cho đến khi dashboard cấp thật; chưa khẳng định giữ được đúng subdomain.

Nếu dùng New → Web Service thủ công: repo root context `.`, Dockerfile `./Dockerfile`, runtime Docker, branch main, Free, health `/actuator/health`; nhập cùng các env như Blueprint.

Nếu GitHub là organization, tài khoản đăng nhập phải được phép cài Render GitHub App vào đúng repo. Không đổi repo sang public hay cấp toàn bộ repo chỉ để vượt lỗi quyền.

## 5. Vercel — Admin và Client

Hai repo đã có workflow GitHub CI (Node 24, check, test, build) và `vercel.json`.

Trong từng Vercel project:
1. Settings → Git: kiểm tra đúng repo và Production Branch. FE hiện làm việc trên `develop`; chọn branch theo quy trình chủ dự án đang dùng, không tự đổi branch/source.
2. Settings → Build and Deployment: Node.js **24.x**.
3. Settings → Environment Variables: nhập các giá trị trong `.env.cloud.example` của chính repo đó; thay placeholder bằng URL backend thật.
4. Redeploy để áp dụng env. Với `VITE_*` / `NEXT_PUBLIC_*`, chỉ sửa env mà không build lại sẽ chưa đổi được code đã phát hành.

Admin:
```dotenv
VITE_DATA_SOURCE=HTTP
VITE_BACKEND_PROVIDER=JAVA
VITE_API_BASE_URL=https://<backend-thật>.onrender.com
VITE_API_PREFIX=/api/v1
```

Client:
```dotenv
NEXT_PUBLIC_BACKEND_PROVIDER=JAVA
API_BE_URL=https://<backend-thật>.onrender.com
API_BE_PREFIX=/api/v1
NEXT_PUBLIC_CONTENT_API_URL=https://<backend-thật>.onrender.com
NEXT_PUBLIC_CONTENT_API_PREFIX=/api/v1
NEXT_PUBLIC_SITE_URL=https://jewelry-ecommerce-client.vercel.app
AUTH_COOKIE_SECURE=true
```

Không gộp `/api/v1` vào cả base URL lẫn prefix. Không dùng `localhost` trên các bản cloud.

GitHub CI và auto-deploy Vercel là hai cơ chế riêng: mặc định Vercel có thể build cùng lúc với CI, không khẳng định test fail sẽ tự chặn Vercel production. Muốn chặn, cấu hình branch protection/checks và merge vào production branch sau CI; không tắt test để deploy.

## 6. Tài khoản Admin và dữ liệu demo

DB rỗng không tự có Admin/sản phẩm/CMS. Không import toàn bộ SQL mẫu có mật khẩu công khai.

Tạo tài khoản người dùng qua chức năng đăng ký được hỗ trợ, dùng SĐT chủ dự án và mật khẩu riêng mạnh. Sau đó chủ DB dùng Neon SQL Editor cấp ADMIN cho đúng tài khoản, ví dụ SĐT chủ dự án đã xác nhận:

```sql
BEGIN;
INSERT INTO roles (name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.phone = '0796406514' AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
COMMIT;
```

Đối chiếu bảng/cột với migration hiện tại trước khi chạy. Nếu chưa đăng ký, SELECT không có user và không cấp được role; không tạo user với password_hash giả. Đăng nhập lại sau khi cấp role. Tài khoản cloud email và SĐT đăng nhập ứng dụng là hai khái niệm khác nhau.

Dữ liệu demo chọn một phương án, không tự làm cả hai:
- **Tạo mới qua API:** tạo sản phẩm/CMS/ảnh trực tiếp trên cloud sau khi chủ dự án xác nhận đích. Script local `create-java-demo.mjs` có guard chỉ cho local; không tắt guard để ép chạy cloud.
- **Chuyển DB local:** sao lưu trước, rà Flyway history/schema phiên bản và địa chỉ ảnh. Không restore đè lên DB cloud đã có dữ liệu; không trộn restore và migration tùy tiện.

Các ảnh `http://localhost:...` trong dữ liệu cũ phải được upload lên Cloudinary và cập nhật đúng tham chiếu. Chưa xem chuyển dữ liệu là hoàn tất chỉ vì backend health UP.

## 7. Xác nhận sau deploy

- Health UP; Flyway thành công; không lỗi memory.
- Admin login, danh sách sản phẩm và CMS đọc được.
- Upload một ảnh → URL Cloudinary HTTPS → Client tải được; ảnh còn sau redeploy.
- Client mở home/list/detail; đặt COD và đơn hiện trong Admin.
- Hai FE không gọi localhost; không có CORS error.
- Chỉ kiểm tra quyền bằng tài khoản của chủ dự án, không public mật khẩu.

Bản đồ án chưa được coi là sẵn sàng kinh doanh thật chỉ vì deploy thành công; các lỗi nghiệp vụ đã có trong report cần theo dõi riêng.

## 8. Sao lưu và buổi bảo vệ

- Giữ bản sao DB và ảnh gốc ngoài dịch vụ, không commit dữ liệu khách.
- Free plans có hạn mức và có thể đổi chính sách; kiểm tra dashboard trước buổi bảo vệ.
- Mở health trước demo để backend thức; không cần chạy lại mọi test case.
- Giữ bản local hoạt động và video demo dự phòng.
- Tài liệu chính thức: https://render.com/docs/free ; https://render.com/docs/blueprint-spec ; https://neon.com/pricing ; https://cloudinary.com/pricing ; https://cloudinary.com/documentation/authentication_signatures
