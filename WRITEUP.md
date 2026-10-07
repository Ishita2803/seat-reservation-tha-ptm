# Writeup

## Atomic decision & deadlock avoidance

A reserve is one `@Transactional` method (`ReservationService.doReserve`):

1. Look up the show (404 if missing).
2. Lock the requested seats: `SELECT ... FROM seats WHERE show_id=? AND label IN (?) ORDER BY label FOR UPDATE` — Spring Data JPA's `@Lock(PESSIMISTIC_WRITE)` plus a JPQL query with `ORDER BY s.id.label`. Verified via SQL logging that Hibernate actually emits `order by s1_0.label for no key update`.
3. If any requested seat doesn't exist → 404. If any locked seat isn't effectively available (confirmed, or held and not yet expired) → roll back → 409 `seat_taken`. All-or-nothing: a partial request never partially succeeds.
4. Per-user limit check (below), then update the locked seats to `held`, insert the reservation and the idempotency row, commit.

**Why sorted lock order prevents deadlock:** a multi-seat request locks rows in label order every time, regardless of the order seats were requested in. Two requests that both want `{A1, A3}` and `{A3, A1}` both end up acquiring `A1` before `A3`, so one blocks behind the other instead of each holding what the other needs — the classic two-lock deadlock is structurally impossible.

**Why wait, not `NOWAIT`:** if a seat is locked by an in-flight transaction and we used `NOWAIT`, every concurrent waiter gets declined immediately — including the "500 people on one seat" case, where that would mean *zero* winners if the lock holder later rolls back. Waiting means the seat ends with exactly one winner: whoever is still in the queue when the lock holder commits or rolls back re-reads the current row (Postgres's default READ COMMITTED re-checks on wake) and either finds it available or correctly declines.

**Lock order across the whole app, not just reserve:** seats (sorted) → the per-user mutex row (`user_show_locks`) during reserve; the reservation row → that reservation's seats (sorted) during confirm/cancel. These two paths never compete for the same lock in reverse order — reserve never holds a lock on an existing reservation row (it's creating a brand new one), so there's no cycle between the two code paths.

## Idempotency

`idempotency_keys(user_id, key, request_hash, reservation_id, response)`, primary key
`(user_id, key)`. The key comes from the `Idempotency-Key` header or the `idempotency_key`
body field; it's required.

The request hash covers `show_id` + the exact seats array, so the same key reused with a
different body is detectably different.

**Flow**, all inside `ReservationService.reserve`:
1. Look up `(user_id, key)` first, before touching any seat lock. If found and the hash matches, return the stored response as a replay — no re-locking, no re-deciding. If found with a different hash, 409 `idempotency_key_reuse`. This upfront check is what makes a *sequential* retry (arriving after the original committed) replay correctly instead of being declined `seat_taken` against its own earlier hold.
2. If no key exists yet, run the full reserve. At the very end, `saveAndFlush` the idempotency row in the *same* transaction as the reservation and the seat updates.
3. If that insert hits the unique constraint (two truly concurrent first-attempts with the same brand-new key), it throws `DataIntegrityViolationException`, which kills this transaction and its persistence context. The controller — a plain, non-transactional method — catches that and calls `IdempotencyReplayService.resolveReplay` in a **fresh** transaction (`REQUIRES_NEW`) to read the now-committed row and either replay it or return 409 on a hash mismatch.
4. There's a second, subtler race: two concurrent identical requests contending for the *same seat lock* (not just the same key). The loser wakes up after the winner commits, sees the seat taken, and is about to decline — but a decline for an idempotent request that actually won elsewhere isn't a real decline. So `reserve` re-checks the idempotency key again right before letting any `DeclineException` propagate, and replays if a sibling request has since committed under that key.

Found two bugs building this (see commit history for `Add idempotency keys`): first, `Seat` and `IdempotencyKey` both have manually-assigned ids, which makes Spring Data's `save()` silently `merge()` (select-then-update) instead of inserting — defeating the unique-constraint check entirely for a genuine key collision. Fixed by implementing `Persistable` on both so `save()` does a real insert. Second, the sequential-retry-declines-as-seat_taken bug described in step 1 above.

If the first attempt is declined (seat taken, over limit), the whole transaction rolls back, so no idempotency row is ever written — a retry with the same key just tries again from scratch, as intended.

## Holds & expiry

Model B: `POST /shows/{id}/reserve` holds seats for a 5-minute TTL (`app.hold-ttl-minutes`),
returning `"status": "held"`. Confirming is a separate step
(`POST /reservations/{id}/confirm`) — the assignment's sample response shows `"confirmed"`,
but this service deliberately returns `held`; confirm is the deliberate "yes, I actually want
this" step, matching how a real checkout works (cart hold → payment → confirmation).

**Lazy expiry (correctness):** a seat with `status='held'` and `expires_at` in the past is
treated as available by every code path that reads it — `Seat.effectiveStatus()`, used by
`isEffectivelyAvailable` in reserve, by `GET /shows/{id}`'s counts, and by `confirm`'s expiry
check. Correctness never depends on anything running a background sweep.

**Sweeper (state hygiene):** `ExpirySweeper.sweep()`, `@Scheduled` every 5s
(`app.sweep-interval-ms`), runs two independent `@Modifying` bulk updates — one on `seats`, one
on `reservations` — both guarded by `WHERE status='held' AND expires_at < now()`. That guard is
what makes the sweeper provably safe to run concurrently with everything else: a bulk `UPDATE`
always locks the rows it touches, so if a seat is mid-confirm when the sweeper's statement
reaches it, the sweeper blocks until confirm commits, then re-evaluates its `WHERE` clause
against the now-current row — which is `confirmed`, not `held`, so the sweeper skips it. No
explicit coordination between the sweeper and confirm/cancel is needed; it falls out of
Postgres's MVCC + row locking.

**Expiry-vs-confirm race:** `confirm` takes a `PESSIMISTIC_WRITE` lock on the reservation row,
then checks `status == 'held' && !isExpired`. If the hold already expired, confirm doesn't just
decline — it opportunistically releases the seats and marks the reservation `expired` right
there (the same lazy-expiry principle extended one step further), then returns 409
`hold_expired`. This means a confirm attempt on an expired hold frees the seat immediately for
someone else, rather than waiting up to one sweep interval.

## Consistency vs. availability under partition

This service always chooses consistency over availability: every seat decision goes through a
row lock on the single Postgres primary, so if Postgres is unreachable, the service can't
decide — and it says so (`/readyz` returns 503, reserve/confirm/cancel would fail their query
and surface as a 5xx rather than silently guessing). There's no mode where two replicas each
independently grant the same seat because they couldn't see each other; there's exactly one
place a seat's status lives, and nothing proceeds without a lock on it.

The deliberate trade-off: a user can be declined when the system is actually fine but briefly
can't prove it (e.g., a Hikari pool queued under load, or a transient network blip to Postgres)
— "decline and let them retry" is always safer than "guess yes and maybe oversell," given the
assignment's explicit bar is "no seat ever confirmed to two users," not "never decline
spuriously."

## Observability & 2am pages

What should actually page someone at 2am, from the metrics and logs this service produces:

- **Any 5xx.** The whole design pushes every expected outcome to a 4xx; a 5xx means something is broken, not that a user was unlucky.
- **Reconciliation drift**: `seats_available + seats_held + seats_confirmed != total_seats` for any show, read from `/metrics` gauges vs. `GET /shows/{id}`. If these two ever disagree, the invariant the whole assignment is graded on has broken.
- **`/readyz` failing** for more than the time it should take Postgres to recover — this is the system saying "I can't make correct decisions right now."
- **p99 latency on `/shows/{id}/reserve`** climbing — the usual early signal of lock contention or pool exhaustion before it becomes visible as declines or timeouts.
- **Hikari pool saturation** (connections waiting) — the generous-timeout philosophy means requests queue instead of failing fast, which is good for correctness but means a saturated pool shows up as latency, not errors, until it's severe. Worth a dedicated alert rather than relying on 5xx to catch it.
- **Sweeper lag**: no sweep log line (`swept N expired seat holds...`) for several intervals in a row would mean the scheduler thread is stuck — state hygiene degrades slowly and silently, so it needs its own watch rather than waiting for a user to notice a stale seat.

## AI usage

Pending — see `AI_USAGE.md` (not yet written).

## What's next

- **Payment integration**: a real payment step between hold and confirm, so `confirm` only succeeds after a payment provider confirms the charge, not just on the user's say-so.
- **Waitlist**: when a hot seat is declined, offer to queue for it instead of just failing — notify if it frees up before the hold would expire anyway.
- **Rate limiting**: nothing currently throttles a single user's or IP's request rate; the per-user reservation limit caps *successful holds*, not request volume, so a determined client can still hammer the seat-lock path.
- **Seat categories/pricing**: `price_paise` is flat per show today; real venues have tiers (front-row vs. balcony) that would need a per-seat (not per-show) price.
- **Notifying users before hold expiry**: a hold just silently expires; a "your hold expires in 60s" push/websocket notice would reduce accidental drop-offs.
- **Read replicas / sharding hot shows**: the whole design intentionally centralizes every seat decision on one primary for correctness; if a single show's write volume ever exceeded one Postgres primary's capacity, the next step would be sharding by show_id (seats for a given show always live on the same shard, so row locks stay local) rather than naively adding read replicas (which don't help write-heavy lock contention at all).
