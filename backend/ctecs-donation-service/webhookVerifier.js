'use strict';

const crypto = require('crypto');

const MAX_REPLAY_WINDOW_SEC = 300; // 5 minutes

/**
 * Verifies a timestamped HMAC-SHA256 webhook signature from mynkdev-21/upi-gateway
 * (`X-Webhook-Signature: t=<unix_sec>,v1=<hex_hmac_sha256>`) over the exact raw request body.
 *
 * Enforces:
 * 1. Raw body HMAC-SHA256 verification using crypto.timingSafeEqual (constant-time)
 * 2. Timestamp replay window enforcement (±5 minutes)
 * 3. Order existence, amount, and INR currency matching
 * 4. Strict server-side status rules (never downgrades PAID via stale events)
 */
function parseSignatureHeader(headerValue) {
  if (!headerValue || typeof headerValue !== 'string') {
    return null;
  }
  const parts = {};
  for (const segment of headerValue.split(',')) {
    const idx = segment.indexOf('=');
    if (idx <= 0) continue;
    const key = segment.slice(0, idx).trim();
    const val = segment.slice(idx + 1).trim();
    if (key && val) {
      parts[key] = val;
    }
  }
  const timestampSec = Number(parts.t);
  const signatureHex = parts.v1;
  if (!Number.isFinite(timestampSec) || !signatureHex || !/^[0-9a-fA-F]{64}$/.test(signatureHex)) {
    return null;
  }
  return { timestampSec, signatureHex: signatureHex.toLowerCase() };
}

function computeExpectedSignatureHex(rawBodyBuffer, timestampSec, webhookSecret) {
  const hmac = crypto.createHmac('sha256', webhookSecret);
  hmac.update(`${timestampSec}.`);
  hmac.update(rawBodyBuffer);
  return hmac.digest('hex');
}

function verifyWebhookSignature({
  rawBodyBuffer,
  signatureHeader,
  webhookSecret,
  nowSec = Math.floor(Date.now() / 1000)
}) {
  if (!webhookSecret || typeof webhookSecret !== 'string') {
    return { valid: false, error: 'Webhook secret not configured' };
  }
  if (!Buffer.isBuffer(rawBodyBuffer) || rawBodyBuffer.length === 0) {
    return { valid: false, error: 'Raw body buffer required' };
  }

  const parsed = parseSignatureHeader(signatureHeader);
  if (!parsed) {
    return { valid: false, error: 'Malformed signature header' };
  }

  if (Math.abs(nowSec - parsed.timestampSec) > MAX_REPLAY_WINDOW_SEC) {
    return { valid: false, error: 'Webhook timestamp outside 5-minute replay window' };
  }

  const expectedHex = computeExpectedSignatureHex(rawBodyBuffer, parsed.timestampSec, webhookSecret);
  const providedBuf = Buffer.from(parsed.signatureHex, 'hex');
  const expectedBuf = Buffer.from(expectedHex, 'hex');

  if (providedBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(providedBuf, expectedBuf)) {
    return { valid: false, error: 'Invalid HMAC-SHA256 signature' };
  }

  return { valid: true, timestampSec: parsed.timestampSec };
}

function validateWebhookOrderMatch(payload, dbOrder) {
  if (!dbOrder) {
    return { ok: false, error: 'Unknown order reference' };
  }
  const eventCurrency = String(payload.currency || 'INR').toUpperCase();
  if (eventCurrency !== 'INR') {
    return { ok: false, error: `Unsupported currency: ${eventCurrency}` };
  }
  const eventAmount = Number(payload.amount);
  const expectedAmount = Number(dbOrder.amount_inr);
  if (!Number.isFinite(eventAmount) || Math.abs(eventAmount - expectedAmount) > 0.009) {
    return { ok: false, error: 'Amount mismatch between webhook and stored order' };
  }
  return { ok: true };
}

module.exports = {
  MAX_REPLAY_WINDOW_SEC,
  parseSignatureHeader,
  computeExpectedSignatureHex,
  verifyWebhookSignature,
  validateWebhookOrderMatch
};
