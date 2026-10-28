#!/usr/bin/env bash
# Tạo file .env với mật khẩu DB ngẫu nhiên và cặp khoá JWT mới.
# Dùng:  bash gen-secrets.sh <domain> [url-FE-1,url-FE-2,...]
# Ví dụ: bash gen-secrets.sh api.example.com https://jewelry-ecommerce-client.vercel.app
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ -f .env ]; then
  echo "LỖI: .env đã tồn tại. Xoá hoặc đổi tên nó nếu thật sự muốn sinh lại" >&2
  echo "     (sinh lại khoá JWT sẽ làm mọi phiên đăng nhập hiện có hết hiệu lực)." >&2
  exit 1
fi
DOMAIN="${1:?Thiếu tham số: bash gen-secrets.sh <domain> [url-FE]}"
CORS="${2:-https://jewelry-ecommerce-client.vercel.app}"
command -v openssl >/dev/null || { echo "LỖI: cần cài openssl" >&2; exit 1; }

tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
# genrsa + pkcs8: cho ra PKCS#8 ("BEGIN PRIVATE KEY") trên cả OpenSSL 1.x lẫn 3.x, đúng định dạng app đọc.
openssl genrsa 2048 2>/dev/null | openssl pkcs8 -topk8 -nocrypt -out "$tmp"
PRIV="$(grep -v '^-----' "$tmp" | tr -d '\n')"
PUB="$(openssl pkey -in "$tmp" -pubout | grep -v '^-----' | tr -d '\n')"
DBPASS="$(openssl rand -hex 24)"

umask 077
sed -e "s|^DOMAIN=.*|DOMAIN=${DOMAIN}|" \
    -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${DBPASS}|" \
    -e "s|^JWT_PRIVATE_KEY=.*|JWT_PRIVATE_KEY=${PRIV}|" \
    -e "s|^JWT_PUBLIC_KEY=.*|JWT_PUBLIC_KEY=${PUB}|" \
    -e "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${CORS}|" \
    .env.example > .env
echo "Đã tạo .env cho domain ${DOMAIN} (quyền chỉ chủ sở hữu đọc được). Hãy sao lưu file này ở nơi an toàn."
