'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  computeExpectedSignatureHex,
  verifyWebhookSignature,
  validateWebhookOrderMatch
} = require('./webhookVerifier');

test('accepts valid timestamped raw-body HMAC-SHA256 webhook signature', () => {
  const secret = 'test_webhook_secret_1234567890';
  const rawBody = Buffer.from(JSON.stringify({ event_id: 'evt_1', client_txn_id: 'CTECS123', amount: 50, currency: 'INR', event: 'order.paid' }), 'utf8');
  const nowSec = 1700000000;
  const sigHex = computeExpectedSignatureHex(rawBody, nowSec, secret);
  const res = verifyWebhookSignature({
    rawBodyBuffer: rawBody,
    signatureHeader: `t=${nowSec},v1=${sigHex}`,
    webhookSecret: secret,
    nowSec: nowSec + 30
  });
  assert.equal(res.valid, true);
});

test('rejects tampered raw body signature', () => {
  const secret = 'test_webhook_secret_1234567890';
  const rawBody = Buffer.from('{"amount":50}', 'utf8');
  const tamperedBody = Buffer.from('{"amount":500}', 'utf8');
  const nowSec = 1700000000;
  const sigHex = computeExpectedSignatureHex(rawBody, nowSec, secret);
  const res = verifyWebhookSignature({
    rawBodyBuffer: tamperedBody,
    signatureHeader: `t=${nowSec},v1=${sigHex}`,
    webhookSecret: secret,
    nowSec
  });
  assert.equal(res.valid, false);
});

test('rejects replayed webhook outside 5-minute tolerance', () => {
  const secret = 'test_webhook_secret_1234567890';
  const rawBody = Buffer.from('{"amount":20}', 'utf8');
  const oldTs = 1700000000;
  const sigHex = computeExpectedSignatureHex(rawBody, oldTs, secret);
  const res = verifyWebhookSignature({
    rawBodyBuffer: rawBody,
    signatureHeader: `t=${oldTs},v1=${sigHex}`,
    webhookSecret: secret,
    nowSec: oldTs + 301
  });
  assert.equal(res.valid, false);
});

test('enforces exact order amount and INR currency match', () => {
  const dbOrder = { order_ref: 'CTECS1', amount_inr: '50.00', currency: 'INR' };
  assert.equal(validateWebhookOrderMatch({ amount: 50, currency: 'INR' }, dbOrder).ok, true);
  assert.equal(validateWebhookOrderMatch({ amount: 20, currency: 'INR' }, dbOrder).ok, false);
  assert.equal(validateWebhookOrderMatch({ amount: 50, currency: 'USD' }, dbOrder).ok, false);
});
