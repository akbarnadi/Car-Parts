# Security model

## Threat boundaries

The browser is never trusted to generate product codes, authorize roles, calculate the authoritative workflow state, or expose internal costs. Those decisions are enforced in Java/PostgreSQL.

## Authentication

- BCrypt password hashes, cost 12.
- 256-bit random session token; only SHA-256 token hash stored.
- HttpOnly + SameSite=Lax session cookie.
- CSRF protection on authenticated state-changing API calls.
- Configurable Secure cookie for HTTPS production.
- User disable operation invalidates sessions.
- First administrator is bootstrapped only from environment variables and only if no users exist.

## Authorization

Spring Security URL rules protect product mutation, lookup administration, user administration, news administration and manager operations. Role-aware panels are convenience UI; backend authorization remains authoritative.

## Data integrity

- Foreign keys for workflow/order relationships.
- Check constraints for states and non-negative money.
- Product codes remain unique/immutable.
- Vehicle brand must belong to selected vehicle type.
- Used code-table values cannot be renumbered by the admin API.
- Product delete is soft delete.
- Audit/workflow event records preserve important changes.

## Production checklist

Use HTTPS, set `AUTH_SECURE_COOKIE=true`, use a strong DB password, restrict `CORS_ALLOWED_ORIGINS`, rotate bootstrap credentials after creating the first account, place PostgreSQL on a private network, back up DB/object storage, add rate limiting/WAF at reverse proxy, configure email/phone verification if customer accounts become transactional, and move images to controlled object storage with MIME/size scanning before enabling direct upload.

## Known next hardening work

Field-level finance permissions, login rate limiting, password reset/email verification, MFA for privileged users, secret manager integration, document malware scanning, idempotency keys for payments, structured audit export, and automated backup restore tests.
