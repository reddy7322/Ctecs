'use strict';

const crypto = require('crypto');
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const { verifyWebhookSignature, validateWebhookOrderMatch } = require('./webhookVerifier');

const PORT = Number(process.env.PORT || 8443);
const RECIPIENT_UPI_ID = process.env.DONATION_RECIPIENT_UPI_ID || 'tvtelugu@ptyes';
const RECIPIENT_NAME = process.env.DONATION_RECIPIENT_NAME || 'madhu';
const CURRENCY = 'INR';
const DESCRIPTION = process.env.DONATION_DESCRIPTION || 'CTECS Coffee Support';
const MIN_AMOUNT_INR = Number(process.env.DONATION_MIN_AMOUNT_INR || 1);
const MAX_AMOUNT_INR = Number(process.env.DONATION_MAX_AMOUNT_INR || 50000);
const ORDER_EXPIRY_MS = 15 * 60 * 1000;

const UPI_GATEWAY_BASE_URL = (process.env.UPI_GATEWAY_BASE_URL || '').replace(/\/+$/, '');
const UPI_GATEWAY_API_KEY = process.env.UPI_GATEWAY_API_KEY || '';
const UPI_GATEWAY_API_SECRET = process.env.UPI_GATEWAY_API_SECRET || '';
const UPI_GATEWAY_WEBHOOK_SECRET = process.env.UPI_GATEWAY_WEBHOOK_SECRET || '';
const UPI_GATEWAY_ACCOUNT_ID = process.env.UPI_GATEWAY_ACCOUNT_ID || '';

const ADMIN_USER_ID = process.env.ADMIN_USER_ID || '';
const ADMIN_PASSPHRASE_SALT = process.env.ADMIN_PASSPHRASE_SALT || '';
const ADMIN_PASSPHRASE_HASH = process.env.ADMIN_PASSPHRASE_HASH || '';
const ADMIN_TOKEN_SIGNING_SECRET = process.env.ADMIN_TOKEN_SIGNING_SECRET || '';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

const app = express();
app.disable('x-powered-by');
app.use(helmet());

// Rate limiting on payment creation, UTR submission, webhook, and admin routes
const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false
});
const adminAuthLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false
});

function buildCanonicalUpiUri(amountInr, orderRef) {
  const params = new URLSearchParams({
    pa: RECIPIENT_UPI_ID,
    pn: RECIPIENT_NAME,
    tr: orderRef,
    tn: DESCRIPTION,
    am: Number(amountInr).toFixed(2),
    cu: CURRENCY
  });
  return `upi://pay?${params.toString()}`;
}

function validateAmountInr(raw) {
  const str = String(raw ?? '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(str)) {
    return { valid: false, error: 'Amount must be a positive number with up to 2 decimals.' };
  }
  const value = Number(str);
  if (!Number.isFinite(value) || value < MIN_AMOUNT_INR || value > MAX_AMOUNT_INR) {
    return { valid: false, error: `Amount must be between INR ${MIN_AMOUNT_INR} and INR ${MAX_AMOUNT_INR}.` };
  }
  return { valid: true, value };
}

function signAdminToken(adminId) {
  if (!ADMIN_TOKEN_SIGNING_SECRET) {
    throw new Error('ADMIN_TOKEN_SIGNING_SECRET is not configured');
  }
  const exp = Math.floor(Date.now() / 1000) + 3600; // 1 hour
  const payload = Buffer.from(JSON.stringify({ sub: adminId, role: 'ADMIN', exp })).toString('base64url');
  const sig = crypto.createHmac('sha256', ADMIN_TOKEN_SIGNING_SECRET).update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

function verifyAdminBearer(req, res, next) {
  const auth = req.header('Authorization') || '';
  const match = auth.match(/^Bearer\s+(.+)$/);
  if (!match || !ADMIN_TOKEN_SIGNING_SECRET) {
    return res.status(401).json({ error: 'Administrator authentication required.' });
  }
  const [payload, sig] = match[1].split('.');
  if (!payload || !sig) {
    return res.status(401).json({ error: 'Malformed bearer token.' });
  }
  const expectedSig = crypto.createHmac('sha256', ADMIN_TOKEN_SIGNING_SECRET).update(payload).digest('base64url');
  const sigBuf = Buffer.from(sig);
  const expectedBuf = Buffer.from(expectedSig);
  if (sigBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(sigBuf, expectedBuf)) {
    return res.status(401).json({ error: 'Invalid administrator token.' });
  }
  try {
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (!claims.sub || claims.role !== 'ADMIN' || claims.exp < Math.floor(Date.now() / 1000)) {
      return res.status(401).json({ error: 'Expired or invalid administrator session.' });
    }
    req.adminId = claims.sub;
    return next();
  } catch {
    return res.status(401).json({ error: 'Invalid token payload.' });
  }
}

/**
 * Calls the self-hosted mynkdev-21/upi-gateway Merchant API (`POST /api/v1/orders`)
 * strictly server-side using X-Api-Key and X-Api-Secret headers.
 */
async function createGatewayOrder({ orderRef, amountInr }) {
  if (!UPI_GATEWAY_BASE_URL || !UPI_GATEWAY_API_KEY || !UPI_GATEWAY_API_SECRET) {
    return null;
  }
  const body = {
    amount: Number(amountInr),
    client_txn_id: orderRef,
    customer_name: 'CTECS Supporter',
    p_info: DESCRIPTION
  };
  if (UPI_GATEWAY_ACCOUNT_ID) {
    body.account_id = UPI_GATEWAY_ACCOUNT_ID;
  }
  const resp = await fetch(`${UPI_GATEWAY_BASE_URL}/api/v1/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': UPI_GATEWAY_API_KEY,
      'X-Api-Secret': UPI_GATEWAY_API_SECRET
    },
    body: JSON.stringify(body)
  });
  if (!resp.ok) {
    throw new Error(`UPI Gateway order creation failed with HTTP ${resp.status}`);
  }
  return resp.json();
}

async function fetchGatewayOrderStatus(gatewayOrderId) {
  if (!gatewayOrderId || !UPI_GATEWAY_BASE_URL || !UPI_GATEWAY_API_KEY || !UPI_GATEWAY_API_SECRET) {
    return null;
  }
  const resp = await fetch(`${UPI_GATEWAY_BASE_URL}/api/v1/orders/${encodeURIComponent(gatewayOrderId)}`, {
    method: 'GET',
    headers: {
      'X-Api-Key': UPI_GATEWAY_API_KEY,
      'X-Api-Secret': UPI_GATEWAY_API_SECRET
    }
  });
  if (!resp.ok) return null;
  return resp.json();
}

// =============================================================================
// 1. WEBHOOK ENDPOINT (Mounted BEFORE express.json so exact raw body is preserved)
// =============================================================================
app.post(
  '/api/webhooks/upi-gateway',
  apiLimiter,
  express.raw({ type: 'application/json', limit: '64kb' }),
  async (req, res) => {
    const sigHeader = req.header('X-Webhook-Signature') || req.header('X-Signature') || '';
    const sigCheck = verifyWebhookSignature({
      rawBodyBuffer: req.body,
      signatureHeader: sigHeader,
      webhookSecret: UPI_GATEWAY_WEBHOOK_SECRET
    });
    if (!sigCheck.valid) {
      return res.status(401).json({ error: sigCheck.error });
    }

    let payload;
    try {
      payload = JSON.parse(req.body.toString('utf8'));
    } catch {
      return res.status(400).json({ error: 'Invalid JSON payload' });
    }

    const eventId = String(payload.event_id || payload.id || '').trim();
    const orderRef = String(payload.client_txn_id || payload.order_ref || '').trim();
    const eventType = String(payload.event || payload.status || '').trim().toLowerCase();
    if (!eventId || !orderRef) {
      return res.status(400).json({ error: 'Missing event_id or order reference' });
    }

    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // Event deduplication
      const dup = await client.query('SELECT 1 FROM donation_webhook_events WHERE event_id = $1', [eventId]);
      if (dup.rowCount > 0) {
        await client.query('COMMIT');
        return res.status(200).json({ ok: true, duplicate: true });
      }

      const orderRes = await client.query('SELECT * FROM donation_orders WHERE order_ref = $1 FOR UPDATE', [orderRef]);
      const order = orderRes.rows[0];
      const match = validateWebhookOrderMatch(payload, order);
      if (!match.ok) {
        await client.query('ROLLBACK');
        return res.status(422).json({ error: match.error });
      }

      const nowMs = Date.now();
      const payloadHash = crypto.createHash('sha256').update(req.body).digest('hex');

      await client.query(
        `INSERT INTO donation_webhook_events (event_id, order_ref, event_type, payload_sha256, processed_at_ms)
         VALUES ($1, $2, $3, $4, $5)`,
        [eventId, orderRef, eventType, payloadHash, nowMs]
      );

      // Safe ordering: never downgrade an already PAID order
      if (order.status !== 'PAID') {
        let nextStatus = order.status;
        let serverVerified = order.server_verified;
        let verifiedAtMs = order.verified_at_ms;
        const confirmationRef = String(payload.utr || payload.gateway_txn_id || eventId);

        if (eventType === 'order.paid' || eventType === 'paid') {
          nextStatus = 'PAID';
          serverVerified = true;
          verifiedAtMs = nowMs;
        } else if (eventType === 'order.expired' || eventType === 'expired') {
          nextStatus = 'EXPIRED';
        } else if (eventType === 'order.failed' || eventType === 'failed') {
          nextStatus = 'FAILED';
        }

        await client.query(
          `UPDATE donation_orders
           SET status = $1, server_verified = $2, confirmation_ref = COALESCE(NULLIF($3, ''), confirmation_ref),
               utr = COALESCE(NULLIF($4, ''), utr), verified_at_ms = $5, updated_at_ms = $6
           WHERE order_ref = $7`,
          [nextStatus, serverVerified, confirmationRef, String(payload.utr || ''), verifiedAtMs, nowMs, orderRef]
        );

        await client.query(
          `INSERT INTO donation_audit_logs (order_ref, previous_status, new_status, actor_role, actor_id, evidence, reason, timestamp_ms)
           VALUES ($1, $2, $3, 'GATEWAY_WEBHOOK', 'upi_gateway', $4, $5, $6)`,
          [orderRef, order.status, nextStatus, `event=${eventId}`, `Verified signed webhook ${eventType}`, nowMs]
        );
      }

      await client.query('COMMIT');
      return res.status(200).json({ ok: true });
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('Webhook processing error:', err.message);
      return res.status(500).json({ error: 'Internal webhook error' });
    } finally {
      client.release();
    }
  }
);

// Standard JSON parser for non-webhook routes
app.use(express.json({ limit: '32kb' }));

// =============================================================================
// 2. DONOR ENDPOINTS (Idempotent order creation, status polling, UTR submission)
// =============================================================================
app.post('/api/donations/orders', apiLimiter, async (req, res) => {
  const donorId = String(req.header('X-Donor-Session') || req.body.donor_id || '').trim();
  const idempotencyKey = String(req.header('Idempotency-Key') || '').trim();
  if (!donorId || donorId.length < 6 || !idempotencyKey || idempotencyKey.length < 8) {
    return res.status(400).json({ error: 'Valid donor session and Idempotency-Key header are required.' });
  }

  const check = validateAmountInr(req.body.amount);
  if (!check.valid) {
    return res.status(400).json({ error: check.error });
  }

  try {
    // Idempotency lookup prevents duplicate orders on retries/double-taps
    const existing = await pool.query('SELECT * FROM donation_orders WHERE idempotency_key = $1', [idempotencyKey]);
    if (existing.rowCount > 0) {
      const row = existing.rows[0];
      return res.status(200).json({
        order_ref: row.order_ref,
        gateway_order_id: row.gateway_order_id,
        upi_uri: row.upi_uri,
        status: row.status,
        amount: Number(row.amount_inr),
        currency: row.currency
      });
    }

    const nowMs = Date.now();
    const orderRef = String(req.body.order_ref || `CTECS${nowMs % 100000000}${crypto.randomBytes(4).toString('hex').toUpperCase()}`);
    let gatewayOrderId = '';
    let upiUri = buildCanonicalUpiUri(check.value, orderRef);

    const gatewayResp = await createGatewayOrder({ orderRef, amountInr: check.value });
    if (gatewayResp) {
      gatewayOrderId = String(gatewayResp.id || gatewayResp.data?.id || '');
      const candidateUri = String(gatewayResp.upi_uri || gatewayResp.data?.upi_uri || '');
      if (candidateUri.startsWith('upi://pay') && candidateUri.includes(RECIPIENT_UPI_ID)) {
        upiUri = candidateUri;
      }
    }

    await pool.query(
      `INSERT INTO donation_orders
       (order_ref, gateway_order_id, idempotency_key, owner_user_id, amount_inr, currency, upi_id, recipient_name, description, upi_uri, status, created_at_ms, updated_at_ms, expires_at_ms)
       VALUES ($1, $2, $3, $4, $5, 'INR', $6, $7, $8, $9, 'CREATED', $10, $10, $11)`,
      [orderRef, gatewayOrderId, idempotencyKey, donorId, check.value, RECIPIENT_UPI_ID, RECIPIENT_NAME, DESCRIPTION, upiUri, nowMs, nowMs + ORDER_EXPIRY_MS]
    );

    return res.status(201).json({
      order_ref: orderRef,
      gateway_order_id: gatewayOrderId,
      upi_uri: upiUri,
      status: 'CREATED',
      amount: check.value,
      currency: CURRENCY
    });
  } catch (err) {
    console.error('Create order error:', err.message);
    return res.status(502).json({ error: 'Unable to create donation order right now.' });
  }
});

app.get('/api/donations/orders/:orderRef', apiLimiter, async (req, res) => {
  const donorId = String(req.header('X-Donor-Session') || '').trim();
  const orderRef = String(req.params.orderRef || '').trim();
  if (!donorId || !orderRef) {
    return res.status(400).json({ error: 'Donor session and orderRef required.' });
  }

  try {
    const result = await pool.query(
      'SELECT * FROM donation_orders WHERE order_ref = $1 AND owner_user_id = $2',
      [orderRef, donorId]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Order not found.' });
    }
    const order = result.rows[0];

    // Optional live poll of self-hosted gateway if still pending
    if ((order.status === 'CREATED' || order.status === 'PENDING') && order.gateway_order_id) {
      const gw = await fetchGatewayOrderStatus(order.gateway_order_id);
      const gwStatus = String(gw?.status || gw?.data?.status || '').toUpperCase();
      if (gwStatus === 'PAID') {
        const nowMs = Date.now();
        await pool.query(
          `UPDATE donation_orders SET status = 'PAID', server_verified = TRUE, verified_at_ms = $1, updated_at_ms = $1 WHERE order_ref = $2`,
          [nowMs, orderRef]
        );
        order.status = 'PAID';
        order.server_verified = true;
        order.verified_at_ms = nowMs;
      }
    }

    return res.json({
      order_ref: order.order_ref,
      status: order.status,
      verified: Boolean(order.server_verified && order.status === 'PAID'),
      amount: Number(order.amount_inr),
      currency: order.currency,
      utr: order.utr || '',
      confirmation_ref: order.confirmation_ref || '',
      verified_at_ms: order.verified_at_ms ? Number(order.verified_at_ms) : null
    });
  } catch (err) {
    console.error('Get order error:', err.message);
    return res.status(500).json({ error: 'Failed to retrieve order status.' });
  }
});

// Donor UTR submission marks UNDER_REVIEW ONLY — NEVER marks PAID!
app.post('/api/donations/orders/:orderRef/utr', apiLimiter, async (req, res) => {
  const donorId = String(req.header('X-Donor-Session') || '').trim();
  const orderRef = String(req.params.orderRef || '').trim();
  const utr = String(req.body.utr || '').trim().toUpperCase();
  if (!donorId || !orderRef || !/^[A-Z0-9]{8,22}$/.test(utr)) {
    return res.status(400).json({ error: 'Valid donor session, orderRef, and 8-22 char UTR are required.' });
  }

  try {
    const result = await pool.query(
      'SELECT * FROM donation_orders WHERE order_ref = $1 AND owner_user_id = $2',
      [orderRef, donorId]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error: 'Order not found.' });
    }
    const order = result.rows[0];
    if (order.status === 'PAID') {
      return res.json({ order_ref: orderRef, status: 'PAID', verified: true });
    }

    const nowMs = Date.now();
    await pool.query(
      `UPDATE donation_orders SET utr = $1, status = 'UNDER_REVIEW', updated_at_ms = $2 WHERE order_ref = $3`,
      [utr, nowMs, orderRef]
    );
    await pool.query(
      `INSERT INTO donation_audit_logs (order_ref, previous_status, new_status, actor_role, actor_id, evidence, reason, timestamp_ms)
       VALUES ($1, $2, 'UNDER_REVIEW', 'DONOR', $3, $4, 'Donor submitted UTR for manual review', $5)`,
      [orderRef, order.status, donorId, `UTR:${utr}`, nowMs]
    );

    return res.json({ order_ref: orderRef, status: 'UNDER_REVIEW', verified: false });
  } catch (err) {
    console.error('UTR submit error:', err.message);
    return res.status(500).json({ error: 'Unable to record UTR.' });
  }
});

// =============================================================================
// 3. PROTECTED ADMINISTRATOR AUTH & EVIDENCE-BASED VERIFICATION
// =============================================================================
app.post('/api/admin/auth/login', adminAuthLimiter, (req, res) => {
  const adminId = String(req.body.admin_id || '').trim();
  const passphrase = String(req.body.passphrase || '');
  if (!ADMIN_USER_ID || !ADMIN_PASSPHRASE_SALT || !ADMIN_PASSPHRASE_HASH || !ADMIN_TOKEN_SIGNING_SECRET) {
    return res.status(503).json({ error: 'Admin authentication is not configured on server.' });
  }
  const computed = crypto.pbkdf2Sync(passphrase, Buffer.from(ADMIN_PASSPHRASE_SALT, 'hex'), 120000, 32, 'sha256').toString('hex');
  const idMatch = adminId.toLowerCase() === ADMIN_USER_ID.toLowerCase();
  const hashMatch = crypto.timingSafeEqual(Buffer.from(computed, 'hex'), Buffer.from(ADMIN_PASSPHRASE_HASH, 'hex'));
  if (!idMatch || !hashMatch) {
    return res.status(401).json({ error: 'Invalid administrator credentials.' });
  }
  return res.json({ token: signAdminToken(ADMIN_USER_ID), admin_id: ADMIN_USER_ID });
});

app.post('/api/admin/donations/:orderRef/verify', apiLimiter, verifyAdminBearer, async (req, res) => {
  const orderRef = String(req.params.orderRef || '').trim();
  const approve = Boolean(req.body.approve);
  const evidence = String(req.body.evidence || '').trim();
  const reason = String(req.body.reason || '').trim();

  if (evidence.length < 6 || reason.length < 5) {
    return res.status(400).json({ error: 'Independent bank/statement verification evidence and reason are required.' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const found = await client.query('SELECT * FROM donation_orders WHERE order_ref = $1 FOR UPDATE', [orderRef]);
    if (found.rowCount === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Donation order not found.' });
    }
    const order = found.rows[0];
    const nowMs = Date.now();
    const nextStatus = approve ? 'PAID' : 'FAILED';
    const confirmRef = approve ? `ADM-${orderRef.slice(-6)}-${nowMs % 10000}` : order.confirmation_ref;

    await client.query(
      `UPDATE donation_orders
       SET status = $1, server_verified = $2, confirmation_ref = $3, verified_at_ms = $4, updated_at_ms = $5
       WHERE order_ref = $6`,
      [nextStatus, approve, confirmRef, approve ? nowMs : order.verified_at_ms, nowMs, orderRef]
    );
    await client.query(
      `INSERT INTO donation_audit_logs (order_ref, previous_status, new_status, actor_role, actor_id, evidence, reason, timestamp_ms)
       VALUES ($1, $2, $3, 'ADMIN', $4, $5, $6, $7)`,
      [orderRef, order.status, nextStatus, req.adminId, evidence, reason, nowMs]
    );
    await client.query('COMMIT');
    return res.json({ ok: true, order_ref: orderRef, status: nextStatus, confirmation_ref: confirmRef });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Admin verify error:', err.message);
    return res.status(500).json({ error: 'Verification failed.' });
  } finally {
    client.release();
  }
});

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`CTECS Donation Service listening on port ${PORT} (recipient: ${RECIPIENT_NAME} / ${RECIPIENT_UPI_ID})`);
  });
}

module.exports = { app };
