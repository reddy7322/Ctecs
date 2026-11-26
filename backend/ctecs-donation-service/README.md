# CTECS Trusted Donation Backend (`ctecs-donation-service`)

Trusted server-side backend for the CTECS Android **Buy Me a Coffee ☕** feature, integrating the self-hosted open-source gateway [`https://github.com/mynkdev-21/upi-gateway`](https://github.com/mynkdev-21/upi-gateway).

---

## 1. Exact Recipient Configuration (Immutable)

| Field | Value |
|---|---|
| UPI ID | `tvtelugu@ptyes` |
| Recipient Name | `madhu` (never `TVTelugu`) |
| Currency | `INR (₹)` |
| Description | `CTECS Coffee Support` |

---

## 2. Source-Code Audit of `mynkdev-21/upi-gateway`

Before integrating, `https://github.com/mynkdev-21/upi-gateway` was audited for capabilities, security posture, and production limitations:

- **Architecture**: Self-hosted Laravel 12 multi-merchant UPI gateway exposing a Merchant REST API (`POST /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET /api/v1/accounts`) authenticated via server-side `X-Api-Key` and `X-Api-Secret` headers (`test_key_` / `test_sec_` in sandbox).
- **Webhooks**: Emits `order.paid` and `order.expired` events signed with timestamped HMAC-SHA256 (`t=<unix_sec>,v1=<hex_sig>`) over the exact raw request body with a 5-minute replay tolerance and `event_id` deduplication.
- **Provider Support & Critical Production Limitation**:
  - **Official Merchant Gateways (Paytm PG / PhonePe PG)**: Provide official signed server-side settlement verification.
  - **Generic Personal/Business UPI ID (e.g., `tvtelugu@ptyes` without merchant API)**: Standard NPCI UPI IDs do **not** expose an automatic bank settlement verification API. Payments sent to a generic UPI ID **cannot** be auto-verified from client UPI app intents or donor-entered UTRs.
  - **Paytm for Business Poller (`GATEWAY_ENABLE_PAYTM_BUSINESS`)**: Unofficial merchant polling — disabled by default and should NOT be relied on without explicit merchant authorization.
- **CTECS Security Posture**:
  - Client UPI app return values (`Status=SUCCESS`, `RESULT_OK`), QR scans, and donor-submitted UTR numbers **NEVER** mark a donation `PAID`.
  - Donor UTR submissions transition orders strictly to `UNDER_REVIEW`.
  - Only a **verified signed server webhook** (when using an official merchant provider) OR an **authenticated administrator action with mandatory bank-statement evidence and audit reason** may transition a donation to `PAID`.
  - Zero merchant API keys, secrets, webhook secrets, database credentials, or `APP_KEY` values are embedded inside the CTECS Android APK.

---

## 3. Production Deployment & Verification Checklist

> **IMPORTANT**: Live payment processing is **not** active until you deploy both services over HTTPS, configure real merchant/admin secrets, verify `tvtelugu@ptyes` (`madhu`) receipt ownership, and complete real end-to-end test transactions.

1. Deploy `mynkdev-21/upi-gateway` behind HTTPS (TLS 1.2+) with its own hardened PostgreSQL/MySQL database and webhook secret.
2. Run `/backend/ctecs-donation-service/migrations/001_create_donations.sql` against your PostgreSQL database.
3. Copy `.env.example` to `.env` on your server (never commit `.env` or embed it in the APK) and populate all secrets (`UPI_GATEWAY_API_KEY`, `UPI_GATEWAY_API_SECRET`, `UPI_GATEWAY_WEBHOOK_SECRET`, PBKDF2 admin hash/salt, `ADMIN_TOKEN_SIGNING_SECRET`).
4. Point `mynkdev-21/upi-gateway` webhook delivery to `https://<your-ctecs-backend>/api/webhooks/upi-gateway`.
5. Configure the CTECS Android app's trusted backend HTTPS endpoint to your deployed `ctecs-donation-service` URL.
6. Perform real ₹1–₹10 live test transactions on physical Android devices (GPay, PhonePe, Paytm, BHIM) and verify:
   - Exact recipient displays `madhu` (`tvtelugu@ptyes`)
   - Unverified returns remain `PENDING` / `UNDER_REVIEW`
   - Signed webhooks or authenticated admin bank-statement verification transition status to `PAID` and write immutable audit logs.
