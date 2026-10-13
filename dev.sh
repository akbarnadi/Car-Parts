#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$ROOT_DIR/.dev"
ENV_FILE="$ROOT_DIR/.env.dev"
FRONTEND_ENV="$ROOT_DIR/frontend/.env.local"
BACKEND_LOG="$DEV_DIR/backend.log"
FRONTEND_LOG="$DEV_DIR/frontend.log"
BACKEND_PID_FILE="$DEV_DIR/backend.pid"
FRONTEND_PID_FILE="$DEV_DIR/frontend.pid"

mkdir -p "$DEV_DIR"

info()  { printf '\033[1;34m[dev]\033[0m %s\n' "$*"; }
ok()    { printf '\033[1;32m[dev]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[dev]\033[0m %s\n' "$*"; }
fail()  { printf '\033[1;31m[dev]\033[0m %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' پیدا نشد. ابتدا آن را نصب کنید."
}

stop_pid_file() {
  local pid_file="$1"
  local label="$2"
  if [[ ! -f "$pid_file" ]]; then return; fi
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    info "Stopping $label (PID $pid)…"
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill -TERM "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.2
    done
    pkill -KILL -P "$pid" 2>/dev/null || true
    kill -KILL "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
}

cleanup_apps() {
  stop_pid_file "$FRONTEND_PID_FILE" "frontend"
  stop_pid_file "$BACKEND_PID_FILE" "backend"
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local pid="$3"
  local log_file="$4"
  local attempts="${5:-90}"

  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      ok "$label آماده است."
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      warn "$label قبل از آماده شدن متوقف شد. آخرین لاگ‌ها:"
      tail -n 60 "$log_file" 2>/dev/null || true
      return 1
    fi
    sleep 1
  done

  warn "$label در زمان مورد انتظار آماده نشد. آخرین لاگ‌ها:"
  tail -n 60 "$log_file" 2>/dev/null || true
  return 1
}

ensure_dev_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    require_command openssl
    local random_password
    random_password="Dev-$(openssl rand -hex 12)!"
    cat > "$ENV_FILE" <<EOF
# Local development only. This file is ignored by git.
BOOTSTRAP_ADMIN_EMAIL=manager@local.test
BOOTSTRAP_ADMIN_PASSWORD=$random_password
BOOTSTRAP_ADMIN_NAME=Development_Manager
AUTH_SECURE_COOKIE=false
CORS_ALLOWED_ORIGINS=http://localhost:3000
EOF
    chmod 600 "$ENV_FILE" 2>/dev/null || true
    ok "فایل .env.dev ساخته شد و یک رمز مدیر محلی تصادفی ایجاد شد."
  fi

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

ensure_frontend_env() {
  if [[ ! -f "$FRONTEND_ENV" ]]; then
    if [[ -f "$ROOT_DIR/frontend/.env.example" ]]; then
      cp "$ROOT_DIR/frontend/.env.example" "$FRONTEND_ENV"
    else
      printf 'NEXT_PUBLIC_API_URL=http://localhost:8080/api\n' > "$FRONTEND_ENV"
    fi
    ok "frontend/.env.local ساخته شد."
  fi
}

check_ports() {
  if command -v lsof >/dev/null 2>&1; then
    if lsof -nP -iTCP:8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
      fail "پورت 8080 در حال استفاده است. ابتدا './dev.sh stop' را اجرا کنید یا پردازش موجود را ببندید."
    fi
    if lsof -nP -iTCP:3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
      fail "پورت 3000 در حال استفاده است. ابتدا './dev.sh stop' را اجرا کنید یا پردازش موجود را ببندید."
    fi
  fi
}

start_all() {
  require_command docker
  require_command java
  require_command mvn
  require_command node
  require_command npm
  require_command curl

  docker info >/dev/null 2>&1 || fail "Docker Desktop اجرا نیست. Docker Desktop را باز کنید و دوباره تلاش کنید."

  cleanup_apps
  check_ports
  ensure_dev_env
  ensure_frontend_env

  local java_version
  java_version="$(java -version 2>&1 | head -n 1 || true)"
  if [[ "$java_version" != *"21"* ]]; then
    warn "این پروژه برای Java 21 تنظیم شده است. نسخه فعلی: $java_version"
  fi

  info "Starting PostgreSQL…"
  (cd "$ROOT_DIR" && docker compose up -d postgres)

  info "Waiting for PostgreSQL…"
  for _ in {1..60}; do
    if (cd "$ROOT_DIR" && docker compose exec -T postgres pg_isready -U car_parts -d car_parts >/dev/null 2>&1); then
      ok "PostgreSQL آماده است."
      break
    fi
    sleep 1
  done
  (cd "$ROOT_DIR" && docker compose exec -T postgres pg_isready -U car_parts -d car_parts >/dev/null 2>&1) || fail "PostgreSQL آماده نشد. 'docker compose logs postgres' را بررسی کنید."

  if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    info "اولین اجرا: نصب dependencyهای frontend…"
    (cd "$ROOT_DIR/frontend" && npm ci)
  fi

  : > "$BACKEND_LOG"
  : > "$FRONTEND_LOG"

  info "Starting Spring Boot…"
  (
    cd "$ROOT_DIR/backend"
    exec mvn spring-boot:run
  ) > "$BACKEND_LOG" 2>&1 &
  local backend_pid=$!
  echo "$backend_pid" > "$BACKEND_PID_FILE"

  wait_for_url "http://localhost:8080/actuator/health" "Backend" "$backend_pid" "$BACKEND_LOG" 120 || {
    cleanup_apps
    exit 1
  }

  info "Starting Next.js…"
  (
    cd "$ROOT_DIR/frontend"
    exec npm run dev
  ) > "$FRONTEND_LOG" 2>&1 &
  local frontend_pid=$!
  echo "$frontend_pid" > "$FRONTEND_PID_FILE"

  wait_for_url "http://localhost:3000" "Frontend" "$frontend_pid" "$FRONTEND_LOG" 120 || {
    cleanup_apps
    exit 1
  }

  printf '\n'
  ok "کل وب‌سایت در حالت Development اجرا شد."
  printf '  Website:        http://localhost:3000\n'
  printf '  Backend API:    http://localhost:8080\n'
  printf '  Backend health: http://localhost:8080/actuator/health\n'
  printf '  PostgreSQL:     localhost:5432\n'
  printf '\n'
  printf '  مدیر توسعه:     %s\n' "${BOOTSTRAP_ADMIN_EMAIL:-manager@local.test}"
  printf '  رمز مدیر:       %s\n' "${BOOTSTRAP_ADMIN_PASSWORD:-see .env.dev}"
  printf '\n'
  printf '  Backend log:    .dev/backend.log\n'
  printf '  Frontend log:   .dev/frontend.log\n'
  printf '  Live logs:      make logs\n'
  printf '\n'
  info "برای توقف frontend و backend کلید Ctrl+C را بزنید. PostgreSQL برای اجرای بعدی روشن می‌ماند."
  info "برای توقف کامل: ./dev.sh stop"

  if [[ "$(uname -s)" == "Darwin" && "${DEV_NO_OPEN:-0}" != "1" ]]; then
    open "http://localhost:3000" >/dev/null 2>&1 || true
  fi

  trap 'cleanup_apps; printf "\n"; info "Frontend و backend متوقف شدند. PostgreSQL روشن باقی ماند."; exit 0' INT TERM
  trap 'cleanup_apps' EXIT

  while true; do
    if ! kill -0 "$backend_pid" 2>/dev/null; then
      warn "Backend متوقف شد. آخرین لاگ‌ها:"
      tail -n 80 "$BACKEND_LOG" || true
      return 1
    fi
    if ! kill -0 "$frontend_pid" 2>/dev/null; then
      warn "Frontend متوقف شد. آخرین لاگ‌ها:"
      tail -n 80 "$FRONTEND_LOG" || true
      return 1
    fi
    sleep 2
  done
}

stop_all() {
  cleanup_apps
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    (cd "$ROOT_DIR" && docker compose stop postgres >/dev/null 2>&1) || true
  fi
  ok "Development stack متوقف شد. داده‌های PostgreSQL حفظ شده‌اند."
}

show_status() {
  printf 'PostgreSQL:\n'
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    (cd "$ROOT_DIR" && docker compose ps postgres) || true
  else
    printf '  Docker Desktop is not running.\n'
  fi
  printf '\nBackend:  '
  curl -fsS http://localhost:8080/actuator/health 2>/dev/null || printf 'not running'
  printf '\nFrontend: '
  if curl -fsS http://localhost:3000 >/dev/null 2>&1; then printf 'running'; else printf 'not running'; fi
  printf '\n'
}

reset_db() {
  require_command docker
  docker info >/dev/null 2>&1 || fail "Docker Desktop اجرا نیست."
  if [[ "${2:-}" != "--yes" ]]; then
    printf 'این کار تمام داده‌های Development PostgreSQL را حذف می‌کند. ادامه؟ [y/N] '
    read -r answer
    [[ "$answer" =~ ^[Yy]$ ]] || { info "لغو شد."; exit 0; }
  fi
  cleanup_apps
  (cd "$ROOT_DIR" && docker compose down -v)
  ok "دیتابیس Development حذف شد. اجرای بعدی ./dev.sh آن را از Flyway می‌سازد."
}

show_logs() {
  touch "$BACKEND_LOG" "$FRONTEND_LOG"
  tail -n 60 -f "$BACKEND_LOG" "$FRONTEND_LOG"
}

case "${1:-start}" in
  start|dev) start_all ;;
  stop) stop_all ;;
  status) show_status ;;
  reset-db) reset_db "$@" ;;
  logs) show_logs ;;
  *)
    cat <<'EOF'
Usage:
  ./dev.sh              Start PostgreSQL + backend + frontend
  ./dev.sh stop         Stop the whole development stack
  ./dev.sh status       Show service status
  ./dev.sh logs         Follow backend/frontend logs
  ./dev.sh reset-db     Delete the local development database volume
  ./dev.sh reset-db --yes   Reset without confirmation
EOF
    exit 1
    ;;
esac
