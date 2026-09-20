#!/usr/bin/env bash
# Creates the test users the load scripts use, in a THROWAWAY stack. Run from the repository root:
#   COMPOSE_PROJECT_NAME=meridian-load ./scripts/load-test/seed.sh
# (bcrypt makes real sign-ups slow and the API rate-limits them to 10 an hour per address, so one
# user is registered normally and its password hash is copied to load1..load360.)
set -euo pipefail
cd "$(dirname "$0")/../.."
BASE="${BASE:-http://localhost:${WEB_PORT:-80}}"
PASSWORD='CorrectHorse!42battery'

curl -s -o /dev/null -w "template user: HTTP %{http_code}\n" -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
  -d "{\"email\":\"template@example.com\",\"password\":\"$PASSWORD\"}"

docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' <<'SQL'
insert into users (email, password_hash, created_at, email_verified)
  with recursive s(n) as (select 1 union all select n+1 from s where n < 360)
  select concat('load', n, '@example.com'),
         (select password_hash from users where email = 'template@example.com'), now(6), b'1' from s;
insert into portfolio (user_id, cash_balance, reserved_cash)
  select id, 10000, 0 from users where email like 'load%@example.com';
select count(*) as load_users from users where email like 'load%@example.com';
SQL
echo "next: python3 scripts/load-test/load.py login 20   (users 1-300 for the mixed runs; 301-360 stay untouched for first_buy_race.py)"
