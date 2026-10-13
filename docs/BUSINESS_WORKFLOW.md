# Business workflow

A `workflow_case` is the operational spine for one customer sourcing journey. Website and offline requests follow the same model.

## Lifecycle

1. **REQUESTED** — website/phone/WhatsApp/email/Instagram/other request received.
2. **PRODUCT_CREATED** — Product Admin or Manager identifies/creates the correct catalog product and attaches it to the request.
3. **SOURCING** — procurement asks suppliers/countries.
4. **SUPPLIER_QUOTED** — one or more supplier quotes recorded.
5. **INTERNAL_COSTED** — landed cost/exchange assumptions reviewed.
6. **CUSTOMER_QUOTED** — offer sent to customer.
7. **CUSTOMER_AGREED** — customer accepted; agreement may originate on-site or be recorded manually for an offline customer.
8. **SUPPLIER_ORDERED** — foreign purchase is placed.
9. **IN_TRANSIT** — international transport.
10. **CUSTOMS** — customs/clearance.
11. **LOCAL_WAREHOUSE** — arrived locally.
12. **READY_FOR_DELIVERY** — local delivery prepared.
13. **DELIVERED** — customer received it.
14. **CLOSED** — commercial/accounting work complete.
15. **CANCELLED** — stopped with history retained.

## Product identification boundary

`/admin/product-requests` intentionally exposes request/product identity without supplier cost, customer margin, or finance details. Product Admin can attach a product to a request. The manager/procurement/finance workflow then continues in the operational workspace.

## Supplier and customer prices

Supplier quotes are append-only sourcing snapshots. Customer quotes separately snapshot supplier cost, logistics, customs, bank/local/other costs, markup, discount, tax, exchange rate, final price and validity. A later market change should create a new quote, not rewrite a historic accepted offer.

## Order, payment and shipping

After agreement, a sales order can be created. Payments are separate records; only confirmed payment increments `paid_amount`. Shipments hold tracking/carrier/origin/destination/ETA and milestones. Shipment status can synchronize the case to `IN_TRANSIT`, `CUSTOMS`, or `DELIVERED`.

## Manual vs automatic tags

Automatic tags are derived from request source and workflow state: website request, sourcing, quote sent, agreement, in transit, customs, delivered. Manual tags capture context that cannot be safely inferred: urgent, VIP, offline agreement, special commercial handling. A system tag should not be manually deleted to rewrite history.

## Future extensions requiring approval

Supplier purchase-order documents, attachments, customs line items, invoice numbering, bank reconciliation, SLA escalation, customer notifications, quote-acceptance links, accounting exports, returns/warranty cases, and approval thresholds can extend the same case model without changing the core product identifier.
