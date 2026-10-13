# Implementation and local runbook

## Prerequisites

- Docker Desktop
- Java 21
- Maven
- Node.js 22 + npm

## First local start

From repository root:

```bash
docker compose up -d postgres
docker compose ps
```

Create the first privileged account only on an empty `app_users` table:

```bash
export BOOTSTRAP_ADMIN_EMAIL='manager@example.com'
export BOOTSTRAP_ADMIN_PASSWORD='replace-with-a-long-unique-password'
export BOOTSTRAP_ADMIN_NAME='مدیر سیستم'
```

Start backend:

```bash
cd backend
mvn spring-boot:run
```

Flyway runs all database migrations. Test:

```text
http://localhost:8080/actuator/health
```

Start frontend in a second terminal:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Open `http://localhost:3000`.

## Normal daily start

```bash
docker compose up -d postgres
```

Then run backend and frontend in separate terminals. Do not run a Homebrew PostgreSQL service on port 5432 while the Docker PostgreSQL container is using the same port.

## Database reset (development only)

```bash
docker compose down -v
docker compose up -d postgres
```

This destroys local test data. Flyway recreates schema on the next backend start.

## Authentication notes

Public catalog/news/request/contact endpoints work without login. Staff/admin mutations require login. The browser obtains a CSRF token from `/api/auth/csrf`; the API client automatically sends it with authenticated state-changing requests. The session cookie is HttpOnly. In production set `AUTH_SECURE_COOKIE=true` and HTTPS.

## Main workspaces

- Public: `/`, `/products`, `/order`, `/news`, `/contact`
- Account: `/login`, `/register`, `/panel`, `/panel/requests`, `/panel/orders`
- Operations: `/manager`, `/manager/cases/{id}`, `/manager/cases/{id}/commerce`
- Product: `/admin/product-requests`, `/admin/products`, `/admin/products/new`, product edit
- Codes: `/admin/codes` and eight dedicated child pages
- Internal references: `/admin/suppliers`, `/admin/tags`
- Administration: `/admin/users`, `/admin/news`, `/admin/inbox`

## Image storage

Product records currently store image URLs and limit each product to 10 images. Direct file upload/object storage is intentionally deferred. When implemented, use private controlled object storage, MIME/size checks, malware scanning, generated thumbnails, and signed/public delivery URLs rather than storing raw image binaries in PostgreSQL.
