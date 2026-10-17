# config/db.config.sh - cấu hình kết nối cho reset-db.sh (được "source" vào, không chạy riêng).
# Mỗi giá trị chỉ được đặt nếu chưa có sẵn trong môi trường, nên có thể ghi đè nhanh:
#   DB_NAME=kltn_demo bash reset-db.sh

: "${PGHOST:=localhost}"
: "${PGPORT:=5432}"
: "${PGUSER:=postgres}"
: "${DB_NAME:=kltn_ecommerce}"

# Mật khẩu: để trống thì psql tự hỏi (ẩn khi gõ). Có thể đặt PGPASSWORD=... nhưng không nên ghi vào file.

# Tên/đường dẫn lệnh psql (đổi nếu psql không nằm trong PATH).
: "${PSQL:=psql}"

# Thư mục chứa 2 file SQL (mặc định: ../../../document tính từ thư mục vps/ chứa reset-db.sh) và tên 2 file.
: "${SQL_DIR:=$HERE/../../../document}"
: "${TABLE_FILE:=1. Table.sql}"
: "${DATA_FILE:=2. Data.sql}"

# Script chỉ cho phép xoá DB trên CHÍNH máy đang chạy (localhost / 127.0.0.1 / ::1 / socket).
# Muốn trỏ tới máy khác phải đặt ALLOW_REMOTE=1 một cách có chủ ý.
: "${ALLOW_REMOTE:=0}"
