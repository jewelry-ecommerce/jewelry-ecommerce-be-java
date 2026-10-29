# Deploy pk-core lên Oracle Cloud (Always Free)

Kiến trúc: một máy ảo (VM) Oracle chạy 3 container bằng Docker Compose.

```
FE (Vercel) --HTTPS--> Caddy (80/443, tự cấp chứng chỉ) --> app Spring Boot (8080, nội bộ) --> PostgreSQL 16 (nội bộ)
```

Chỉ Caddy mở cổng ra ngoài. App và PostgreSQL chỉ nghe trong mạng nội bộ của Docker. Schema do Flyway (V1..V36) tự tạo khi app khởi động lần đầu.

Các file liên quan nằm ở thư mục `deploy/` của repo: `Dockerfile`, `docker-compose.yml`, `Caddyfile`, `.env.example`, `gen-secrets.sh`. Profile `prod` nằm ở `pk-core/pk-service/src/main/resources/application-prod.yml`.

## 0. Cần chuẩn bị

- Tài khoản Oracle Cloud (cần thẻ để xác minh; gói Always Free không bị trừ tiền nếu không vượt giới hạn). Chọn **home region** cẩn thận vì không đổi được, và VM Always Free chỉ tạo được ở home region.
- Một tên miền hoặc subdomain trỏ về IP của VM (Caddy cần để cấp HTTPS; FE trên Vercel chạy HTTPS nên API cũng bắt buộc HTTPS). Chưa có tên miền thì dùng dịch vụ miễn phí như DuckDNS, hoặc dùng `<ip-gạch-ngang>.sslip.io` (ví dụ `129-153-1-2.sslip.io` ứng với IP `129.153.1.2`).
- Cài sẵn `ssh` trên máy bạn.

## 1. Tạo VM

Giới hạn Always Free hiện tại (theo tài liệu Oracle): 1 VM `VM.Standard.A1.Flex` (Arm) tối đa **2 OCPU và 12 GB RAM**, tổng ổ đĩa 200 GB, 10 TB băng thông ra mỗi tháng.

Trong Oracle Cloud Console, vào Compute, Instances, Create instance:

1. **Image**: Ubuntu 22.04 hoặc 24.04 (bản aarch64 cho Arm).
2. **Shape**: Ampere, `VM.Standard.A1.Flex`, đặt 2 OCPU và 12 GB.
3. **Networking**: để tạo VCN mới, chọn **Assign a public IPv4 address**.
4. **SSH keys**: tải khoá riêng (`.key`) về và giữ kỹ.
5. **Boot volume**: 50 đến 100 GB.

Nếu gặp lỗi `Out of host capacity` thì thử availability domain khác hoặc thử lại sau vài giờ; đây là lỗi thiếu máy Always Free tạm thời của Oracle. VM `VM.Standard.E2.1.Micro` (1 GB RAM) quá nhỏ để chạy Spring Boot cùng PostgreSQL, đừng dùng.

Nên đổi IP sang **Reserved public IP** (Networking, IP management) để IP không đổi nếu bạn xoá và tạo lại VM.

Lưu ý: Oracle có thể thu hồi VM Always Free nếu suốt 7 ngày liền CPU, mạng (và RAM với Arm) đều dưới 20%. Một demo ít người dùng có thể rơi vào trường hợp này, nên dùng thì kiểm tra thường xuyên.

## 2. Mở cổng 80 và 443

Phải mở ở **hai nơi**:

**a) Oracle Console**: Networking, Virtual Cloud Networks, chọn VCN, Security Lists, Default Security List, Add Ingress Rules. Thêm 2 luật, Source CIDR `0.0.0.0/0`, giao thức TCP, Destination Port lần lượt `80` và `443`. Cổng 22 (SSH) đã có sẵn.

**b) Firewall trong VM** (image Ubuntu của Oracle có sẵn luật iptables chặn các cổng khác). SSH vào VM rồi chạy:

```bash
ssh -i duong-dan/khoa.key ubuntu@IP_CUA_VM
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt-get update && sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

## 3. Cài Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit    # thoát rồi SSH lại để nhóm docker có hiệu lực
docker compose version
```

## 4. Lấy code và cấu hình

```bash
git clone https://github.com/khanh-duy-003/kltn_ecommerce.git
cd kltn_ecommerce/deploy
bash gen-secrets.sh api.ten-mien-cua-ban.com https://jewelry-ecommerce-client.vercel.app
```

Script tạo file `.env` với mật khẩu DB ngẫu nhiên và cặp khoá JWT mới. Tham số thứ hai là địa chỉ FE được phép gọi API (nhiều địa chỉ thì cách nhau dấu phẩy, không có `/` ở cuối). **Sao lưu file `.env` ở chỗ an toàn**: mất khoá JWT thì mọi phiên đăng nhập hết hiệu lực. Nếu repo là private thì dùng token khi `git clone`.

Trước khi chạy, kiểm tra tên miền đã trỏ đúng về IP của VM (bản ghi A), nếu chưa Caddy sẽ không cấp được chứng chỉ.

## 5. Chạy

```bash
docker compose up -d --build
docker compose logs -f app      # chờ dòng "Started PkServiceApplication", Ctrl+C để thoát xem log
```

Lần build đầu tiên trên 2 OCPU Arm mất vài phút (tải thư viện Maven). Kiểm tra:

```bash
curl https://api.ten-mien-cua-ban.com/actuator/health
# {"status":"UP"}
```

## 6. Dữ liệu mẫu (tuỳ chọn)

Server mới chỉ có schema và hai role mặc định, chưa có tài khoản nào. Để có dữ liệu demo (sản phẩm, đơn hàng, các tài khoản thử):

```bash
set -a; . ./.env; set +a
docker compose exec -T -e PGCLIENTENCODING=UTF8 db \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q < "../document/database/2. Data.sql"
```

Tài khoản admin demo là `0901234567` / `Test@1234`, **mật khẩu này công khai trong repo**. Chỉ để server demo cho thầy cô xem; nếu để lâu thì đổi mật khẩu các tài khoản này.

## 7. Trỏ FE sang API mới

Trên Vercel, đổi biến môi trường chứa địa chỉ API của FE thành `https://api.ten-mien-cua-ban.com`, rồi deploy lại FE. Địa chỉ FE phải có trong `CORS_ALLOWED_ORIGINS` của `.env`. Sửa `.env` xong chạy `docker compose up -d` để nạp lại.

## 8. Vận hành

| Việc | Lệnh (trong thư mục `deploy/`) |
|---|---|
| Xem log app | `docker compose logs -f app` |
| Cập nhật bản mới | `git pull && docker compose up -d --build` |
| Khởi động lại | `docker compose restart app` |
| Dừng tất cả (giữ dữ liệu) | `docker compose down` |
| Sao lưu DB | `set -a; . ./.env; set +a; docker compose exec -T db pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" \| gzip > backup-$(date +%F).sql.gz` |
| Khôi phục DB | `gunzip -c backup.sql.gz \| docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"` (trên DB trống) |

Dữ liệu nằm trong các volume Docker (`pgdata` cho DB, `uploads` cho ảnh tải lên), nên `docker compose down` không mất dữ liệu; **đừng** thêm `-v` vì lệnh đó xoá luôn volume.

Thêm migration mới (V37 trở đi) thì chỉ cần `git pull && docker compose up -d --build`, Flyway tự chạy khi app khởi động.

## 9. Lỗi hay gặp

- **Không vào được từ ngoài**: kiểm tra đã mở cổng 80/443 ở cả Security List lẫn iptables trong VM (mục 2).
- **Caddy báo không lấy được chứng chỉ**: tên miền chưa trỏ về IP VM, hoặc cổng 80 chưa mở.
- **FE báo lỗi CORS**: địa chỉ FE chưa có trong `CORS_ALLOWED_ORIGINS`, hoặc có dấu `/` ở cuối.
- **App không lên**: `docker compose logs app`; thường là sai biến trong `.env`.
- **Hết RAM khi build**: giữ cấu hình 12 GB; nếu đã giảm thì tạo swap hoặc build ở máy khác.
