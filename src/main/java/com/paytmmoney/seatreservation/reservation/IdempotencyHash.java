package com.paytmmoney.seatreservation.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Hashes the parts of a reserve request that must match on a replayed idempotency key. */
final class IdempotencyHash {

    private IdempotencyHash() {
    }

    static String compute(Long showId, List<String> seats) {
        String canonical = showId + ":" + String.join(",", seats);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
