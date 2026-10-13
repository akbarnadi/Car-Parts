# Account, authority, audit, price and notification model

## Identity requirements

Every newly created account has:

- `display_name` — required
- `username` — required and unique, 3–80 letters/numbers/`.`/`_`/`-`
- `phone` — required and unique, normalized before storage
- `email` — optional and unique when supplied
- BCrypt password — minimum 12 characters
- password confirmation — required at account-creation/reset API boundaries
- one or more roles
- optional manager level

Login accepts **username, phone, or email** plus password. Browser sessions are random tokens; only their SHA-256 hashes are stored. The session database timestamp uses an explicit SQL timestamp conversion so PostgreSQL does not receive a raw `java.time.Instant`.

## Manager zero / root account

The first privileged account is the protected root account:

```text
username: manager0
manager_level: 0
is_root: true
roles: SUPER_ADMIN + MANAGER
```

On existing databases, Flyway V4 upgrades the earliest `SUPER_ADMIN` to the root account. On a fresh database the bootstrap environment creates it.

Rules:

1. Only one root account can exist (database unique partial index).
2. Other managers cannot change, disable, reset, or change roles of manager zero.
3. Manager zero can edit its own username/profile and password.
4. `SUPER_ADMIN` cannot be assigned to ordinary accounts through account administration.
5. Other managers are levels 1–99. A manager can manage another non-root manager regardless of that level; levels are operational labels for future approval routing, not hidden privilege escalation.

## Roles

- `SUPER_ADMIN` — reserved for manager zero/root
- `MANAGER` — full business pipeline plus account administration, except root protection
- `ADMIN` — day-to-day customer request, product, tags and safe operational views; no confidential supplier/customer cost workspace and no account-role administration
- `PRODUCT_ADMIN` — product/coding operations
- `PROCUREMENT` — supplier and purchase work
- `FINANCE` — customer quote/order/payment/price work
- `SHIPPING` — shipment/customs/delivery
- `SUPPORT` — requests/contact/content
- `CUSTOMER` — own requests/orders/notifications

A user may have multiple non-root roles. Assigning `CUSTOMER` ensures a linked `customers` row exists so requests/orders can belong to that account.

## Account administration

`/admin/users` is available to manager zero and managers. It supports:

- create customer/admin/manager/specialist accounts manually;
- upgrade or downgrade non-root roles;
- assign manager level 1–99;
- edit username, required phone, optional email and display name;
- activate/disable non-root accounts;
- reset passwords (all sessions are revoked);
- inspect account audit history.

## Durable change history

Flyway V4 installs generic database audit triggers on core mutable tables. Every `INSERT`, `UPDATE`, and `DELETE` writes JSON snapshots to `audit_log` for:

- accounts and roles;
- customers;
- products and product images;
- product code details / allocated serial source;
- all product-code lookup tables;
- product prices;
- customer requests and workflow cases;
- tags and case tags;
- supplier/customer quotations;
- sales orders/items;
- payments;
- shipments.

Application-level audit rows add business meaning such as `PRODUCT_UPDATED`, `PRICE_SET`, `PASSWORD_RESET`, `ATTACH_PRODUCT`, and customer-notification decisions. Database-trigger history remains the safety net if a future code path forgets an application audit call.

Product code/serial allocation is append/history-oriented: the product code is not regenerated during normal product editing. Changes to descriptions, dimensions, images, statuses, pricing, etc. are auditable without rewriting the original identity.

## Product price validity

`product_prices` is historical; a new active price marks the previous active row `REPLACED` instead of overwriting it.

Each price stores:

- product
- amount/currency
- `valid_from`
- `valid_until`
- status
- note
- creator/time

The UI defaults a newly entered price to 60 days of validity, but the manager/finance user can set another date range. Requests linked to a product are automatically classified:

- `VALID` → automatic `price-valid` tag; manager/admin/finance notification that a current price can be used.
- `MISSING` or `EXPIRED` → automatic `price-needed` tag; manager/admin/procurement notification that a new external inquiry is needed.

## Request routing and tags

A customer request can originate from the site or be entered manually. If no product is attached, Product Admin/Admin/Manager receive a product-identification notification. After a product is attached, price validity determines the next recommended action.

Tags retain their source:

- `AUTOMATIC` — facts derived from request/workflow/price state (`site-request`, `price-valid`, `price-needed`, shipping stages, etc.)
- `MANUAL` — business context entered by staff (`VIP`, `urgent`, `offline-agreement`, future custom tags)

Manual tag changes generate workflow events and staff notifications. Automatic tags are not treated as manually removable facts.

## Notifications

`notifications` supports two delivery classes:

1. **In-app** — for users with accounts, shown in `/panel/notifications`.
2. **Queued external/manual** — for customers without accounts, stored with phone/email and intended channel (`MANUAL`, `WHATSAPP`, `SMS`, `EMAIL`). The admin panel exposes the queue and staff can mark manual delivery complete. Actual third-party messaging integrations remain a separate deployment decision.

Events currently capable of generating notifications include:

- new customer request / product identification needed;
- valid price available or price inquiry required;
- manual tag changes;
- customer quote sent;
- sales order created;
- shipment milestone changed;
- product price updated;
- manager/admin explicitly choosing **notify customers** after a product change.

Product edits do **not** automatically message customers. The product operations page provides a deliberate notify action, preserving human control over customer communications.

## Main routes

```text
/register                         customer self-registration
/login                            username/phone/email login
/panel                            role-aware panel
/panel/notifications              personal notification center
/admin/users                      account/role administration
/admin/operations                 admin-safe customer/request board
/admin/products                   product administration
/admin/products/{id}/edit         product editing
/admin/products/{id}/operations   price history + audit history + customer notification
/manager                          manager workflow board
/manager/cases/{id}               sourcing/quotation case
/manager/cases/{id}/commerce      order/payment/shipment
```

## Verification

CI runs a business-logic smoke against a fresh PostgreSQL database. The smoke exercises:

1. customer registration with username + phone and no email;
2. login and session persistence;
3. manager-zero bootstrap/login;
4. creation of manager/admin identities;
5. ordinary manager editing another account;
6. denial when ordinary manager attempts to modify manager zero;
7. admin creating a product;
8. historical product price creation;
9. customer request linked to that product;
10. automatic valid-price routing/tag;
11. staff notification creation;
12. product/product-price audit history.
