# Jewelry Ecommerce — Java backend

Backend chính của đồ án, Java 17 / Spring Boot / PostgreSQL. Nest là phương án backup, không dùng khi deploy repo này.

## Deploy miễn phí

Đọc [hướng dẫn triển khai](deploy/DEPLOY-FREE.md). Tài khoản cloud chủ dự án dùng: **tieuphu142003@gmail.com**. Email đăng ký cloud không tự tạo tài khoản đăng nhập Admin.

- Dockerfile: build Maven từ dependency public, chạy đúng `*-exec.jar`, user không phải root.
- `render.yaml`: Web Service **Free**, không tạo DB/disk trả phí, tự deploy branch `main` sau khi CI pass.
- `.github/workflows/ci.yml`: Java 17, build và chạy cả `*Test` / `*IT` bằng H2; không kết nối DB cloud.
- Profile `cloud`: PostgreSQL TLS, Flyway bật, khóa JWT cố định, ảnh upload Cloudinary.
- Default/local: tiếp tục dùng storage local. Không sửa file env hay DB local của người dùng.

Local vẫn có thể tên folder `kltn_ecommerce`; tên folder không quyết định tên service/domain trên cloud. Không đổi package Java hay tên DB chỉ để đổi tên project.

## Build

Chạy ở gốc repo:

```sh
docker build -t jewelry-ecommerce-be-java .
```

Có JDK 17 và Maven thì build/test:

```sh
cd pk-core
mvn -B -ntp verify "-Dtest=*Test,*IT" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Jar chạy: `pk-core/pk-service/target/pk-service-0.0.1-SNAPSHOT-exec.jar`.

## An toàn dữ liệu

Không chạy `document/database/reset-db.bat`, SQL reset hoặc `2. Data.sql` lên DB cloud. Bản cloud mới để Flyway tạo schema từ DB rỗng; không tự seed tài khoản/mật khẩu mẫu công khai. Không bật đồng thời profile `dev` và `cloud`.

API: `/api/v1`. Health public: `/actuator/health` (không lộ thông tin chi tiết).
