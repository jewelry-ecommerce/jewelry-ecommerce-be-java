#!/usr/bin/env bash
# reset-db.sh - XOÁ SẠCH database rồi tạo lại: chạy "1. Table.sql" rồi "2. Data.sql".
#
# Chạy:  bash reset-db.sh        (cấu hình kết nối ở config/db.config.sh)
# Dùng trên VPS: upload cả thư mục pk-db-tool/vps và thư mục document/ lên VPS, giữ đúng
#   cấu trúc  <gốc>/dev/pk-db-tool/vps  và  <gốc>/document  (script tìm ../../../document),
#   SSH vào VPS rồi chạy lệnh trên - lúc đó PGHOST=localhost của VPS.
#
# Hành vi:
#   - Chỉ chạy với DB trên chính máy này (xem ALLOW_REMOTE trong config/db.config.sh).
#   - Bắt gõ lại tên database trước khi xoá.
#   - Mọi bước chạy trong MỘT phiên psql với ON_ERROR_STOP: lỗi ở bước nào là dừng ngay ở đó.
#     Mỗi file SQL tự bọc BEGIN/COMMIT nên file lỗi thì rollback; nhưng schema cũ đã bị xoá ở
#     bước đầu, nên nếu lỗi thì DB ở trạng thái rỗng/thiếu dữ liệu cho tới lần chạy lại.
#   - Nên tắt (hoặc khởi động lại sau đó) app Spring Boot đang chạy: connection cũ của app có
#     thể còn giữ cache của schema cũ.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config/db.config.sh
source "$HERE/config/db.config.sh"

if ! command -v "$PSQL" >/dev/null 2>&1; then
  echo "LỖI: không tìm thấy psql ('$PSQL'). Cài PostgreSQL client (vd: sudo apt install postgresql-client) hoặc sửa PSQL trong config/db.config.sh." >&2
  exit 1
fi

case "$PGHOST" in
  localhost|127.0.0.1|::1|/*) ;;
  *)
    if [ "$ALLOW_REMOTE" != "1" ]; then
      echo "LỖI: PGHOST='$PGHOST' không phải máy này. Script từ chối xoá DB ở máy khác." >&2
      echo "     Nếu thật sự muốn, đặt ALLOW_REMOTE=1 trong config/db.config.sh." >&2
      exit 1
    fi
    ;;
esac

for f in "$TABLE_FILE" "$DATA_FILE"; do
  if [ ! -f "$SQL_DIR/$f" ]; then
    echo "LỖI: không thấy file: $SQL_DIR/$f" >&2
    exit 1
  fi
done

echo
echo "Sắp XOÁ SẠCH database '$DB_NAME' tại $PGHOST:$PGPORT rồi tạo lại từ đầu."
echo "Toàn bộ bảng và dữ liệu hiện có trong đó sẽ mất."
read -r -p "Gõ lại tên database để xác nhận: " CONFIRM
if [ "$CONFIRM" != "$DB_NAME" ]; then
  echo "Tên không khớp - đã huỷ, không thay đổi gì."
  exit 1
fi

export PGCLIENTENCODING=UTF8
export PGOPTIONS="-c client_min_messages=warning"

echo
echo "Đang chạy..."
if "$PSQL" -X -q -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB_NAME" \
     -c "DROP SCHEMA IF EXISTS public CASCADE" \
     -c "CREATE SCHEMA public" \
     -f "$SQL_DIR/$TABLE_FILE" \
     -f "$SQL_DIR/$DATA_FILE" \
     -c "SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS so_bang, (SELECT count(*) FROM users) AS so_user" \
     -c "SELECT phone AS tai_khoan_dang_nhap, full_name FROM users ORDER BY id"; then
  echo
  echo "XONG: đã xoá, tạo bảng và insert dữ liệu mẫu cho '$DB_NAME'."
  echo "Khởi động lại app Spring Boot nếu nó đang chạy."
else
  echo
  echo "LỖI: psql dừng giữa chừng (xem thông báo ở trên). DB có thể đang rỗng/thiếu dữ liệu - sửa lỗi rồi chạy lại." >&2
  exit 1
fi
