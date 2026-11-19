#!/usr/bin/env bash
# Sao lưu DB production lên Google Drive, giữ đúng KEEP file mới nhất (cuốn chiếu).
#
# Chạy từ workflow .github/workflows/db-backup.yml (hoặc tay trên máy có pg_dump 18 + rclone).
# Biến môi trường:
#   PGHOST, PGPORT, PGUSER, PGDATABASE, PGPASSWORD  kết nối DB (bắt buộc)
#   RCLONE_REMOTE      tên remote rclone đã cấu hình, mặc định "gdrive" (thư mục đích = root của remote)
#   INTERVAL_DAYS      cách nhau tối thiểu bao nhiêu ngày giữa 2 lần sao lưu, mặc định 3
#   KEEP               số file giữ lại trên Drive, mặc định 3
#   FORCE=1            bỏ qua kiểm tra khoảng cách ngày (chạy tay)
#   DRY_RUN=1          chỉ in ra việc sẽ làm, không tải lên / không xóa
set -euo pipefail

REMOTE="${RCLONE_REMOTE:-gdrive}"
INTERVAL_DAYS="${INTERVAL_DAYS:-3}"
KEEP="${KEEP:-3}"
PREFIX="ht_management_"
SUFFIX=".dump"
export TZ="Asia/Ho_Chi_Minh"

: "${PGHOST:?thiếu PGHOST}" "${PGUSER:?thiếu PGUSER}" "${PGDATABASE:?thiếu PGDATABASE}" "${PGPASSWORD:?thiếu PGPASSWORD}"

today="$(date +%F)"
today_s="$(date -d "$today" +%s)"

# Danh sách file sao lưu hiện có trên Drive (tên có dạng ht_management_YYYY-MM-DD.dump), cũ -> mới.
list_backups() {
  rclone lsf "${REMOTE}:" --files-only --include "${PREFIX}????-??-??${SUFFIX}" | sort
}

existing="$(list_backups)"
newest="$(printf '%s\n' "$existing" | sed '/^$/d' | tail -n 1)"

if [[ "${FORCE:-0}" != "1" && -n "$newest" ]]; then
  newest_date="${newest#"$PREFIX"}"; newest_date="${newest_date%"$SUFFIX"}"
  newest_s="$(date -d "$newest_date" +%s)"
  age_days=$(( (today_s - newest_s) / 86400 ))
  if (( age_days < INTERVAL_DAYS )); then
    echo "Bản gần nhất là $newest ($age_days ngày trước, chưa đủ $INTERVAL_DAYS ngày). Bỏ qua lần này."
    exit 0
  fi
fi

name="${PREFIX}${today}${SUFFIX}"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
file="$workdir/$name"

echo "Đang dump $PGDATABASE -> $name"
pg_dump --format=custom --compress=6 --no-owner --no-privileges --file "$file"

# Kiểm tra file dump đọc được và có nội dung trước khi động tới Drive.
size="$(stat -c %s "$file")"
if (( size < 10240 )); then
  echo "File dump quá nhỏ ($size byte), dừng để không xóa nhầm bản cũ." >&2
  exit 1
fi
tables="$(pg_restore --list "$file" | grep -c ' TABLE DATA ' || true)"
if (( tables < 1 )); then
  echo "File dump không có bảng dữ liệu nào, dừng." >&2
  exit 1
fi
echo "Dump hợp lệ: $size byte, $tables bảng dữ liệu."

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  echo "[DRY_RUN] sẽ tải $name lên ${REMOTE}: và xóa các bản cũ, chỉ giữ $KEEP file mới nhất."
  exit 0
fi

# Tải lên rồi mới xóa; rclone copyto kiểm tra kích thước/checksum sau khi tải.
rclone copyto "$file" "${REMOTE}:${name}" --checksum
rclone lsf "${REMOTE}:" --files-only --include "$name" | grep -qx "$name" \
  || { echo "Không thấy $name trên Drive sau khi tải, không xóa gì." >&2; exit 1; }
echo "Đã tải $name lên Drive."

# Xóa vĩnh viễn (không vào thùng rác) các bản cũ, chỉ giữ KEEP file mới nhất. Chỉ đụng file đúng mẫu tên.
all="$(list_backups)"
count="$(printf '%s\n' "$all" | sed '/^$/d' | wc -l)"
if (( count > KEEP )); then
  printf '%s\n' "$all" | sed '/^$/d' | head -n $(( count - KEEP )) | while read -r old; do
    echo "Xóa bản cũ: $old"
    rclone deletefile "${REMOTE}:${old}" --drive-use-trash=false
  done
fi

echo "Còn lại trên Drive:"
list_backups
