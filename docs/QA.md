# Quality assurance

The repository checks the application in multiple independent ways.

## 1. Compile and type integrity

GitHub Actions job `Java + TypeScript build`:

- Java 21 + `mvn verify`
- Node 22 + `npm ci`
- `npx tsc --noEmit`
- Next.js production build
- `npm audit --omit=dev --audit-level=high`

This catches Java compilation, TypeScript contract mismatches, broken Next.js routes/builds, and high-severity production npm dependency advisories.

## 2. Database and backend integration smoke

A clean PostgreSQL 18 service is started in CI. The backend starts against that empty database, Flyway runs V1/V2/V3 in order, Hibernate validates the resulting schema, and CI requires `/actuator/health` to return `UP`.

This specifically catches migration ordering, missing tables/columns, invalid constraints, and startup wiring that compilation alone cannot detect.

## 3. RTL/responsive visual smoke

A second clean PostgreSQL/backend/frontend stack is launched. Playwright opens these public routes at 1440×1000 and 390×844:

- `/`
- `/products`
- `/order`
- `/contact`
- `/news`
- `/login`
- `/register`

The test asserts `html[dir=rtl]`, `lang=fa`, non-empty content, and no unexpected page-level horizontal overflow. Full-page screenshots are uploaded as the `visual-smoke-screenshots` CI artifact for human inspection.

This is a structural visual smoke test, not a pixel-perfect design approval. Before production, a human should review screenshots and authenticated pages with real Persian content and realistic product images.

## 4. Security analysis

CodeQL v4 runs `security-extended` queries for Java/Kotlin and JavaScript/TypeScript when GitHub code scanning is available for the repository. The CodeQL job is non-blocking because private repositories may require GitHub Advanced Security. Backend authorization and threat boundaries are also reviewed manually in `docs/SECURITY.md`.

## Manual release checklist

Before a production release, verify login/logout, CSRF-protected mutations, each role against unauthorized endpoints, lookup-code immutability after product use, car-type/car-brand enforcement, 10-image limit, customer quote snapshots, payment arithmetic, shipment transitions, automatic/manual tags, soft product deletion, responsive Persian layouts, HTTPS/Secure cookie settings, database backups, and restore procedure.
