#!/usr/bin/env bash
# Tao tai khoan super-admin trong DB production (chay 1 lan tren server, sau khi app da len va Flyway da tao bang).
# Cach dung:  cd /opt/ht-management && bash create-super-admin.sh
# Doc SUPER_ADMIN_USERNAME / SUPER_ADMIN_PASSWORD / SUPER_ADMIN_ROLE_CODE tu .env.
# Mat khau duoc bam bcrypt ngay trong Postgres (pgcrypto), tuong thich BCryptPasswordEncoder cua Spring.
set -euo pipefail
cd "$(dirname "$0")"

get() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
DB_USER="$(get DB_USER)"; DB_NAME="$(get DB_NAME)"
SA_USER="$(get SUPER_ADMIN_USERNAME)"; SA_PASS="$(get SUPER_ADMIN_PASSWORD)"; SA_ROLE="$(get SUPER_ADMIN_ROLE_CODE)"

for v in "$SA_USER" "$SA_PASS" "$SA_ROLE"; do
  [[ "$v" =~ ^[A-Za-z0-9_.@-]+$ ]] || { echo "Gia tri chi duoc gom chu, so, _ . @ -"; exit 1; }
done

# Du lieu di qua stdin (khong lo mat khau trong danh sach tien trinh).
docker compose -f compose.prod.yaml exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<SQL
\set u '$SA_USER'
\set p '$SA_PASS'
\set r '$SA_ROLE'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
BEGIN;
INSERT INTO account (username, password, auth_provider, must_change_password, status,
                     created_at, updated_at, created_by, updated_by)
VALUES (:'u', crypt(:'p', gen_salt('bf', 10)), 'LOCAL', FALSE, TRUE, now(), now(), 'SYSTEM', 'SYSTEM');
INSERT INTO account_role (id, username, role_code, is_primary, status,
                          assigned_by, assigned_at, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), :'u', :'r', TRUE, TRUE, 'SYSTEM', now(), now(), now(), 'SYSTEM', 'SYSTEM');
COMMIT;
DROP EXTENSION pgcrypto;
SELECT a.username, ar.role_code FROM account a JOIN account_role ar USING (username);
SQL
echo "Da tao super-admin: $SA_USER"
