#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$ROOT_DIR/.dev"
mkdir -p "$DEV_DIR"

cleanup_apps() {
  local label pid_file pid
  for label in frontend backend; do
    pid_file="$DEV_DIR/$label.pid"
    if [[ -f "$pid_file" ]]; then
      pid="$(cat "$pid_file")"
      if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
        pkill -TERM -P "$pid" 2>/dev/null || true
        kill -TERM "$pid" 2>/dev/null || true
      fi
      rm -f "$pid_file"
    fi
  done
}

load_env() {
  if [[ ! -f "$ROOT_DIR/.env.dev" ]]; then
    umask 077
    cat > "$ROOT_DIR/.env.dev" <<EOF
DB_URL=jdbc:postgresql://127.0.0.1:5432/car_parts
DB_USERNAME=car_parts
DB_PASSWORD=car_parts
DB_HOST=127.0.0.1
DB_PORT=5432
BACKEND_PORT=8080
FRONTEND_PORT=3005
BOOTSTRAP_ADMIN_EMAIL=manager@local.test
BOOTSTRAP_ADMIN_USERNAME=manager0
BOOTSTRAP_ADMIN_PASSWORD=$(openssl rand -hex 24)
BOOTSTRAP_ADMIN_NAME=Development_Manager
AUTH_SECURE_COOKIE=false
EOF
    printf 'Created .env.dev. Configure your native PostgreSQL connection there.\n'
  fi
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.dev"
  set +a
}

wait_for_url() {
  local url="$1" label="$2" pid="$3"
  for _ in {1..120}; do
    curl -fsS "$url" >/dev/null 2>&1 && return 0
    if ! kill -0 "$pid" 2>/dev/null; then
      tail -n 50 "$DEV_DIR/$label.log"
      return 1
    fi
    sleep 1
  done
  tail -n 50 "$DEV_DIR/$label.log"
  return 1
}

start_all() {
  for command in java mvn node npm curl pg_isready openssl; do
    command -v "$command" >/dev/null || { printf 'Install %s first.\n' "$command" >&2; exit 1; }
  done
  load_env
  pg_isready -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" >/dev/null || {
    printf 'Start native PostgreSQL and configure .env.dev first.\n' >&2
    exit 1
  }
  cleanup_apps
  local backend_port="${BACKEND_PORT:-8080}" frontend_port="${FRONTEND_PORT:-3005}"
  export CORS_ALLOWED_ORIGINS="http://localhost:$frontend_port,http://127.0.0.1:$frontend_port"
  [[ -d "$ROOT_DIR/frontend/node_modules" ]] || (cd "$ROOT_DIR/frontend" && npm ci)
  (
    cd "$ROOT_DIR/backend"
    export PORT="$backend_port" BIND_ADDRESS=127.0.0.1
    exec mvn spring-boot:run
  ) > "$DEV_DIR/backend.log" 2>&1 &
  local backend_pid=$!
  echo "$backend_pid" > "$DEV_DIR/backend.pid"
  trap cleanup_apps EXIT
  trap 'exit 0' INT TERM
  wait_for_url "http://127.0.0.1:$backend_port/actuator/health" backend "$backend_pid"
  (
    cd "$ROOT_DIR/frontend"
    export PORT="$frontend_port" BACKEND_URL="http://127.0.0.1:$backend_port" NEXT_PUBLIC_API_URL=/api
    exec npm run dev -- --hostname 127.0.0.1
  ) > "$DEV_DIR/frontend.log" 2>&1 &
  local frontend_pid=$!
  echo "$frontend_pid" > "$DEV_DIR/frontend.pid"
  wait_for_url "http://127.0.0.1:$frontend_port/health" frontend "$frontend_pid"
  printf 'Website: http://localhost:%s\n' "$frontend_port"
  printf 'Manager: %s (password in .env.dev)\n' "${BOOTSTRAP_ADMIN_USERNAME:-manager0}"
  printf 'Native PostgreSQL stays running when the app stops. Press Ctrl+C to stop the app.\n'
  while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do sleep 2; done
  printf 'An application process stopped. Check .dev/*.log.\n' >&2
  return 1
}

case "${1:-start}" in
  start) start_all ;;
  stop) cleanup_apps; printf 'Application stopped; native PostgreSQL remains running.\n' ;;
  status)
    load_env
    pg_isready -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" || true
    curl -fsS "http://127.0.0.1:${FRONTEND_PORT:-3005}/health" || true
    printf '\n'
    ;;
  logs) touch "$DEV_DIR/backend.log" "$DEV_DIR/frontend.log"; tail -f "$DEV_DIR/backend.log" "$DEV_DIR/frontend.log" ;;
  *) printf 'Usage: ./dev.sh [start|stop|status|logs]\n' >&2; exit 2 ;;
esac
