# Implementation and local runbook

Use native PostgreSQL with Java 21, Maven and Node.js 22. Follow [DEVELOPMENT.md](DEVELOPMENT.md) for local database setup and run `./dev.sh` to start the website on port 3005.

For the independent server deployment, see [DEPLOYMENT.md](DEPLOYMENT.md).

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
