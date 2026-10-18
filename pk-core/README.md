# pk-core – Backend Jewelry Commerce (Khóa luận tốt nghiệp)

## Cấu trúc

| Module | Vai trò |
| --- 						| --- 																						|
| `pk-common` 				| Lớp nền dùng chung 																		|
| `pk-model` 				| Thông tin model 																			|
| `pk-identity` 			| Đăng nhập/đăng ký/refresh token, cấu hình bảo mật (JWT RS256) 							|
| `pk-business` 			| Nghiệp vụ dự án 																			|
| `pk-api` 					| Api kết nối																				|
| `pk-service` 				| Module chạy app 																			|
| `pk-unit-test` 			| Unit test và integration test 															|

## 1. Yêu cầu

- JDK 17+,
- Maven
- PostgreSQL

## 2. Chạy PostgreSQL

Các file SQL và bat nằm ở thư mục `document/database/` ở GỐC repo (ngang hàng với `pk-core/`), không nằm trong `pk-core/`:

- `document/database/1. Table.sql`: schema đầy đủ, gộp từ toàn bộ migration Flyway V1..V36 (file sinh tự động, đừng sửa tay)
- `document/database/2. Data.sql`: dữ liệu mẫu (tài khoản demo `0901234567` / `Test@1234`)

**Cách nhanh (Windows):** chạy `document/database/reset-db.bat` - xoá sạch database rồi tạo lại từ 2 file trên.
Cấu hình kết nối (host, port, user, tên DB, đường dẫn `psql.exe`) ở `document/database/config/db.config.bat`.
Không gắn cứng ổ đĩa: đường dẫn SQL tính từ vị trí script, `psql.exe` tự dò (PATH, registry, Program Files). Máy nào cần port/mật khẩu khác thì tạo `document/database/config/db.config.local.bat` (đã được git bỏ qua), ví dụ `set "PGPORT=5433"`.

**Cách tay:** tạo database `kltn_ecommerce`, rồi chạy lần lượt `1. Table.sql` và `2. Data.sql` bằng psql/pgAdmin.

Profile `dev` tắt Flyway (`application-dev.yml`) nên DB dev phải dựng bằng 2 file này. Khi thêm migration mới
(`pk-service/src/main/resources/db/migration/V37__...sql`) thì chạy `python document/database/config/gen_table_sql.py` để sinh lại `1. Table.sql`, rồi chạy lại `reset-db.bat`.

## 3. Chạy ứng dụng (dev, có dữ liệu demo)

- Add Maven project pk-core vào idea
- maven: clean install
- run application run -Dspring-boot.run.profiles=dev

-Kết nối cổng localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html

