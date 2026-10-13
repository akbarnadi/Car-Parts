# Role matrix

Backend authorization is authoritative; hiding a button in the UI is not a security control.

`SUPER_ADMIN` belongs only to the protected **manager zero** root account. Ordinary `MANAGER` accounts may manage any non-root account, including other managers. `ADMIN` is a day-to-day operational role and does not receive confidential sourcing/costing or identity-management permissions.

| Capability | Manager zero / root | Manager | Admin | Product Admin | Procurement | Finance | Shipping | Support | Customer |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Edit own root username/password | ✓ |  |  |  |  |  |  |  |  |
| Modify/disable manager zero | self only |  |  |  |  |  |  |  |  |
| Create/edit non-root accounts | ✓ | ✓ |  |  |  |  |  |  |  |
| Assign non-root roles/manager level | ✓ | ✓ |  |  |  |  |  |  |  |
| Create/edit products | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| Product price/history view | ✓ | ✓ | ✓ | ✓ |  | ✓ |  |  |  |
| Set product selling/reference price | ✓ | ✓ | ✓ |  |  | ✓ |  |  |  |
| Explicitly notify linked customers after product change | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| Manage code tables | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| See product-identification queue | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| Attach product to request | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| Safe request/tag/price-status admin board | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| View full operational cases | ✓ | ✓ |  |  | ✓ | ✓ | ✓ | ✓ |  |
| Create offline request | ✓ | ✓ |  |  |  |  |  | ✓ |  |
| Generic workflow stage override | ✓ | ✓ |  |  |  |  |  |  |  |
| Add/remove manual case tags (safe board) | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| Supplier quotes | ✓ | ✓ |  |  | ✓ |  |  |  |  |
| Supplier directory | ✓ | ✓ |  |  | ✓ |  |  |  |  |
| Customer quote / internal costing | ✓ | ✓ |  |  |  | ✓ |  |  |  |
| Create sales order | ✓ | ✓ |  |  |  | ✓ |  |  |  |
| Record payment | ✓ | ✓ |  |  |  | ✓ |  |  |  |
| Create shipment | ✓ | ✓ |  |  | ✓ |  | ✓ |  |  |
| Change shipment milestone | ✓ | ✓ |  |  |  |  | ✓ |  |  |
| Support inbox | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| News management | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| Personal notifications | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Own requests/orders |  |  |  |  |  |  |  |  | ✓ |

## Manager levels

- `0` — reserved for the single root/manager-zero account.
- `1..99` — labels for ordinary managers. They are stored now so later approval thresholds can use them.
- Levels do not bypass backend role checks and do not permit editing manager zero.

## Customer information vs financial information

`ADMIN` can handle customer needs, product identity, current public/reference price state, tags and queued customer communications. Internal supplier quote values, landed-cost components, margins, customer quotation calculations and payment controls remain inside manager/procurement/finance routes.
