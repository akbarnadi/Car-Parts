#!/usr/bin/env bash
# Install a prepared standalone release with a native PostgreSQL database.
set -Eeuo pipefail
umask 077
[[ "$(id -u)" = 0 ]] || { echo 'Run this installer as root.' >&2; exit 1; }
ARCHIVE="${1:?Usage: install.sh release.tgz}"
PUBLIC_ORIGIN="${2:?Supply the public origin, including port 3005}"
BASE=/opt/car-parts
ENV_DIR=/etc/car-parts
DB_NAME=carparts_3005
DB_ROLE=carparts_3005
DB_PORT=5432
NODE_BIN=/usr/local/bin/node
JAVA_BIN=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
exec 9>/run/lock/car-parts-3005-deploy.lock
flock -n 9 || { echo 'Another Car Parts deployment is running.' >&2; exit 1; }
[[ -f "$ARCHIVE" ]] || { echo 'Release archive is missing.' >&2; exit 1; }
[[ "$PUBLIC_ORIGIN" =~ ^https?://[A-Za-z0-9.:-]+$ ]] || { echo 'Invalid public origin.' >&2; exit 1; }
[[ "$("$NODE_BIN" --version)" == v22.* ]] || { echo 'Node.js 22 is required.' >&2; exit 1; }
pg_isready -h 127.0.0.1 -p "$DB_PORT" >/dev/null || { echo 'Native PostgreSQL is unavailable.' >&2; exit 1; }

for entry in "3005:car-parts-web" "13005:car-parts-backend"; do
  port="${entry%%:*}"
  service="${entry#*:}"
  if ss -ltnH "sport = :$port" | grep -q . && ! systemctl is-active --quiet "$service"; then
    echo "Port $port belongs to another application; deployment stopped." >&2
    exit 1
  fi
done

if [[ ! -x "$JAVA_BIN" ]]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends openjdk-21-jre-headless
fi
id carparts >/dev/null 2>&1 || useradd --system --user-group --home-dir /var/lib/car-parts --shell /usr/sbin/nologin carparts
install -d -m 0755 "$BASE" "$BASE/releases" "$BASE/incoming"
install -d -m 0700 "$BASE/backups" "$ENV_DIR"
install -d -m 0750 -o carparts -g carparts /var/lib/car-parts /var/lib/car-parts/next-cache

if [[ ! -f "$ENV_DIR/backend.env" ]]; then
  DB_PASSWORD="$(openssl rand -hex 32)"
  ADMIN_PASSWORD="$(openssl rand -hex 24)"
  cat > "$ENV_DIR/backend.env" <<EOF
DB_URL=jdbc:postgresql://127.0.0.1:$DB_PORT/$DB_NAME
DB_USERNAME=$DB_ROLE
DB_PASSWORD=$DB_PASSWORD
PORT=13005
BIND_ADDRESS=127.0.0.1
CORS_ALLOWED_ORIGINS=$PUBLIC_ORIGIN,http://127.0.0.1:3005,http://localhost:3005
AUTH_SECURE_COOKIE=false
BOOTSTRAP_ADMIN_USERNAME=manager0
BOOTSTRAP_ADMIN_PHONE=+10000000000
BOOTSTRAP_ADMIN_EMAIL=manager@carparts.local
BOOTSTRAP_ADMIN_NAME=Car_Parts_Manager
BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASSWORD
EOF
  printf '%s\n' "$ADMIN_PASSWORD" > "$ENV_DIR/bootstrap-password"
  chmod 0600 "$ENV_DIR/backend.env" "$ENV_DIR/bootstrap-password"
fi
# This file is generated locally and contains no executable shell expressions.
# shellcheck disable=SC1091
source "$ENV_DIR/backend.env"
[[ "$DB_PASSWORD" =~ ^[0-9a-f]{64}$ ]] || { echo 'Unexpected database credential format.' >&2; exit 1; }
runuser -u postgres -- psql -p "$DB_PORT" --dbname=postgres --quiet --set=ON_ERROR_STOP=1 --set=db_password="$DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE carparts_3005 LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='carparts_3005') \gexec
SELECT 'CREATE DATABASE carparts_3005 OWNER carparts_3005'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='carparts_3005') \gexec
SQL
runuser -u postgres -- pg_dump -p "$DB_PORT" --format=custom "$DB_NAME" > "$BASE/backups/$(date -u +%Y%m%dT%H%M%SZ).dump"

STAGING="$(mktemp -d "$BASE/releases/.staging-XXXXXX")"
trap '[[ ! -d "${STAGING:-}" ]] || rm -rf -- "$STAGING"' EXIT
tar -xzf "$ARCHIVE" -C "$STAGING"
REVISION="$(tr -d '\n' < "$STAGING/REVISION")"
[[ "$REVISION" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid release revision.' >&2; exit 1; }
[[ -f "$STAGING/backend.jar" && -f "$STAGING/web/server.js" ]] || { echo 'Release files are incomplete.' >&2; exit 1; }
RELEASE="$BASE/releases/$REVISION"
if [[ -e "$RELEASE" ]]; then
  rm -rf -- "$STAGING"
else
  mv "$STAGING" "$RELEASE"
fi
STAGING=
chmod -R a+rX "$RELEASE"
rm -rf -- "$RELEASE/web/.next/cache"
ln -s /var/lib/car-parts/next-cache "$RELEASE/web/.next/cache"
PREVIOUS="$(readlink -f "$BASE/current" 2>/dev/null || true)"
ln -sfn "$RELEASE" "$BASE/current.new"
mv -Tf "$BASE/current.new" "$BASE/current"

cat > "$ENV_DIR/web.env" <<EOF
NODE_ENV=production
NEXT_TELEMETRY_DISABLED=1
PORT=3005
HOSTNAME=0.0.0.0
EOF
chmod 0600 "$ENV_DIR/web.env"

cat > /etc/systemd/system/car-parts-backend.service <<EOF
[Unit]
Description=Car Parts Java API
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=carparts
Group=carparts
WorkingDirectory=$BASE/current
EnvironmentFile=$ENV_DIR/backend.env
ExecStart=$JAVA_BIN -Xms128m -Xmx512m -jar $BASE/current/backend.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/car-parts

[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/car-parts-web.service <<EOF
[Unit]
Description=Car Parts website on port 3005
After=network.target car-parts-backend.service
Wants=car-parts-backend.service

[Service]
Type=simple
User=carparts
Group=carparts
WorkingDirectory=$BASE/current/web
EnvironmentFile=$ENV_DIR/web.env
ExecStart=$NODE_BIN $BASE/current/web/server.js
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/car-parts

[Install]
WantedBy=multi-user.target
EOF

rollback() {
  echo 'Deployment health check failed.' >&2
  journalctl -u car-parts-backend -u car-parts-web -n 60 --no-pager >&2
  if [[ -n "$PREVIOUS" && -d "$PREVIOUS" ]]; then
    ln -sfn "$PREVIOUS" "$BASE/current.new"
    mv -Tf "$BASE/current.new" "$BASE/current"
    systemctl restart car-parts-backend car-parts-web
  else
    systemctl stop car-parts-web car-parts-backend || true
  fi
  exit 1
}
wait_healthy() {
  local url="$1"
  for _ in {1..120}; do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  return 1
}
systemctl daemon-reload
systemctl enable car-parts-backend car-parts-web
systemctl restart car-parts-backend
wait_healthy http://127.0.0.1:13005/actuator/health || rollback
systemctl restart car-parts-web
wait_healthy http://127.0.0.1:3005/health || rollback
python3 "$RELEASE/deploy/verify.py" http://127.0.0.1:3005 --admin-password-file "$ENV_DIR/bootstrap-password" || rollback
if command -v ufw >/dev/null && ufw status | grep -q 'Status: active'; then
  ufw allow 3005/tcp
fi
runuser -u postgres -- psql -p "$DB_PORT" --dbname="$DB_NAME" --tuples-only --no-align --command="SELECT count(*) FROM flyway_schema_history WHERE success;"
systemctl is-active car-parts-backend car-parts-web
systemctl is-enabled car-parts-backend car-parts-web
printf 'DEPLOYED_REVISION=%s\n' "$REVISION"
printf 'Native PostgreSQL database: %s on localhost:%s\n' "$DB_NAME" "$DB_PORT"
printf 'Website: %s\n' "$PUBLIC_ORIGIN"
