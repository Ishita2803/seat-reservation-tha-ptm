import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_SECRET = __ENV.ADMIN_SECRET || 'dev-admin-secret';

const confirmed = new Counter('outcome_confirmed');
const declinedSeatTaken = new Counter('outcome_declined_seat_taken');
const declinedPerUserLimit = new Counter('outcome_declined_per_user_limit');
const declinedIdempotencyReuse = new Counter('outcome_declined_idempotency_key_reuse');
const declinedOther = new Counter('outcome_declined_other');
const serverErrors = new Counter('outcome_5xx');

function recordOutcome(res) {
  if (res.status === 201) {
    confirmed.add(1);
  } else if (res.status >= 500) {
    serverErrors.add(1);
    console.error(`5xx: ${res.status} ${res.body}`);
  } else if (res.status === 409) {
    let reason = 'unknown';
    try {
      reason = res.json('error');
    } catch (e) {
      // non-JSON body, leave as unknown
    }
    if (reason === 'seat_taken') declinedSeatTaken.add(1);
    else if (reason === 'per_user_limit') declinedPerUserLimit.add(1);
    else if (reason === 'idempotency_key_reuse') declinedIdempotencyReuse.add(1);
    else declinedOther.add(1);
  } else {
    declinedOther.add(1);
  }
}

function mintToken(userId, admin) {
  const res = http.post(
    `${BASE_URL}/auth/tokens`,
    JSON.stringify({ user_id: userId, admin: admin || false }),
    { headers: { 'Content-Type': 'application/json', 'X-Admin-Secret': ADMIN_SECRET } },
  );
  check(res, { 'mint token 200': (r) => r.status === 200 });
  return res.json('token');
}

function createShow(adminToken, name, seats, pricePaise) {
  const res = http.post(
    `${BASE_URL}/shows`,
    JSON.stringify({ name, seats, price_paise: pricePaise }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` } },
  );
  check(res, { 'create show 201': (r) => r.status === 201 });
  return res.json('id');
}

function seatLabels(prefix, count) {
  const labels = [];
  for (let i = 1; i <= count; i++) {
    labels.push(`${prefix}${i}`);
  }
  return labels;
}

export function setup() {
  // idempotency_keys is scoped by (user_id, key) only, not per show. Against a persistent
  // DB, a fixed key/username run twice would collide with the previous run's leftover row
  // (different show -> different hash -> a real, correct 409, not a bug) -- scope this
  // run's identifiers so two burst runs never share a key.
  const runSeed = Date.now();

  const adminToken = mintToken('burst-admin', true);

  const hotSeatShow = createShow(adminToken, 'burst-hot-seat', ['A12'], 25000);
  const stampedeShow = createShow(adminToken, 'burst-stampede', seatLabels('ST', 1000), 15000);
  const limitShow = createShow(adminToken, 'burst-limit', seatLabels('LIM', 10), 10000);
  const idemShow = createShow(adminToken, 'burst-idempotency', seatLabels('ID', 120), 5000);
  const spoofShow = createShow(adminToken, 'burst-spoof', ['SP1'], 5000);

  const stampedeUsers = [];
  for (let i = 0; i < 200; i++) {
    stampedeUsers.push(mintToken(`stampede-user-${i}`));
  }

  const hotSeatUsers = [];
  for (let i = 0; i < 600; i++) {
    hotSeatUsers.push(mintToken(`hotseat-user-${i}`));
  }

  return {
    runSeed,
    adminToken,
    hotSeatShow,
    stampedeShow,
    limitShow,
    idemShow,
    spoofShow,
    stampedeUsers,
    hotSeatUsers,
    limitUser: mintToken('limit-user'),
  };
}

function reserve(token, showId, seats, idempotencyKey, body) {
  const payload = body || { seats };
  return http.post(`${BASE_URL}/shows/${showId}/reserve`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': idempotencyKey,
    },
  });
}

// Hot-seat storm: every VU fights over the single seat A12. Exactly one wins.
// Key includes runSeed: idempotency_keys is scoped by (user_id, key) only, not per show, so
// a key reused run-to-run against the same persistent DB would collide with the previous
// run's row for a different show -- a real 409 idempotency_key_reuse, not the seat battle.
export function hotSeat(data) {
  const token = data.hotSeatUsers[(__VU - 1) % data.hotSeatUsers.length];
  const res = reserve(token, data.hotSeatShow, ['A12'], `hotseat-${data.runSeed}-${__VU}-${__ITER}`);
  recordOutcome(res);
}

// General stampede: ~20k requests against a 1000-seat show, random seat per request.
export function stampede(data) {
  const token = data.stampedeUsers[Math.floor(Math.random() * data.stampedeUsers.length)];
  const seatIndex = 1 + Math.floor(Math.random() * 1000);
  const res = reserve(token, data.stampedeShow, [`ST${seatIndex}`], `stampede-${__VU}-${__ITER}-${Date.now()}`);
  recordOutcome(res);
}

// One user, 10 parallel reserves, default per_user_limit=4: expect exactly 4 held.
//
// __VU is a globally unique id across the whole k6 run, not reset to 1 per scenario, so a
// seat label derived straight from __VU can land outside this show's 10 seats depending on
// how k6 allocates VU ids to other scenarios. exec.scenario.iterationInTest is a 0-based
// counter scoped to this scenario's own iterations, which is what we actually need here.
export function perUserLimit(data) {
  const seat = `LIM${exec.scenario.iterationInTest + 1}`;
  const res = reserve(
    data.limitUser, data.limitShow, [seat], `limit-${data.runSeed}-${exec.scenario.iterationInTest}`);
  recordOutcome(res);
}

// Same key + same seats retried -> replay (201 again); same key + different seats -> 409.
// Each iteration uses its own fresh user: reusing one user across many iterations would
// hit per_user_limit almost immediately and mask the idempotency behaviour being tested.
export function idempotency(data) {
  const base = exec.scenario.iterationInTest;
  const seatA = `ID${(base * 2) + 1}`;
  const seatB = `ID${(base * 2) + 2}`;
  const key = `idem-${data.runSeed}-${base}`;
  const token = mintToken(`idem-user-${data.runSeed}-${base}`);

  const first = reserve(token, data.idemShow, [seatA], key);
  check(first, { 'idempotency first 201': (r) => r.status === 201 });
  recordOutcome(first);

  const retry = reserve(token, data.idemShow, [seatA], key);
  check(retry, { 'idempotency retry 201 same body': (r) => r.status === 201 });
  recordOutcome(retry);

  const mismatch = reserve(token, data.idemShow, [seatB], key);
  check(mismatch, { 'idempotency mismatch 409': (r) => r.status === 409 });
  recordOutcome(mismatch);
}

// Spoofed user_id in the body must be ignored; the reservation belongs to the token's user.
export function spoofing(data) {
  const token = mintToken(`spoof-user-${data.runSeed}-${__VU}`);
  const res = reserve(token, data.spoofShow, ['SP1'], `spoof-${data.runSeed}-${__VU}`, {
    seats: ['SP1'],
    user_id: 'someone-else-entirely',
  });
  recordOutcome(res);
  check(res, {
    'spoofed user_id ignored': (r) =>
      r.status !== 201 || r.json('user_id') === `spoof-user-${data.runSeed}-${__VU}`,
  });
}

export function teardown(data) {
  console.log('\n--- Reconciliation ---');
  [
    ['hot-seat', data.hotSeatShow],
    ['stampede', data.stampedeShow],
    ['per-user-limit', data.limitShow],
    ['idempotency', data.idemShow],
    ['spoof', data.spoofShow],
  ].forEach(([label, showId]) => {
    const res = http.get(`${BASE_URL}/shows/${showId}`, {
      headers: { Authorization: `Bearer ${data.adminToken}` },
    });
    const counts = res.json('counts');
    const total = res.json('total_seats');
    const sum = counts.available + counts.held + counts.confirmed;
    const ok = sum === total ? 'OK' : 'MISMATCH';
    console.log(
      `${label} (show ${showId}): available=${counts.available} held=${counts.held} ` +
        `confirmed=${counts.confirmed} total=${total} [invariant ${ok}]`,
    );
  });

  const metrics = http.get(`${BASE_URL}/metrics`);
  console.log('\n--- /metrics snippet ---');
  metrics.body
    .split('\n')
    .filter((line) => /^(reservations_|holds_expired_total|seats_)/.test(line))
    .forEach((line) => console.log(line));
}

export const options = {
  scenarios: {
    hot_seat: {
      executor: 'per-vu-iterations',
      exec: 'hotSeat',
      vus: 600,
      iterations: 1,
      startTime: '0s',
      maxDuration: '30s',
    },
    per_user_limit: {
      executor: 'per-vu-iterations',
      exec: 'perUserLimit',
      vus: 10,
      iterations: 1,
      startTime: '15s',
      maxDuration: '15s',
    },
    idempotency: {
      executor: 'per-vu-iterations',
      exec: 'idempotency',
      vus: 6,
      iterations: 10,
      startTime: '25s',
      maxDuration: '30s',
    },
    spoofing: {
      executor: 'per-vu-iterations',
      exec: 'spoofing',
      vus: 5,
      iterations: 1,
      startTime: '45s',
      maxDuration: '15s',
    },
    stampede: {
      executor: 'shared-iterations',
      exec: 'stampede',
      vus: 100,
      iterations: 20000,
      startTime: '55s',
      maxDuration: '3m',
    },
  },
  thresholds: {
    outcome_5xx: ['count==0'],
  },
};
