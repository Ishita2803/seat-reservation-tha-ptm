package com.paytmmoney.seatreservation.reservation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytmmoney.seatreservation.common.DeclineException;

/** Resolves a duplicate idempotency key after the original transaction rolled back. */
@Service
public class IdempotencyReplayService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyReplayService(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponse resolveReplay(String userId, String idempotencyKey, Long showId, List<String> seats) {
        IdempotencyKey existing = idempotencyKeyRepository
                .findById(new IdempotencyKeyId(userId, idempotencyKey))
                .orElseThrow(() -> new IllegalStateException(
                        "idempotency key conflict but no row found for user=%s key=%s".formatted(userId, idempotencyKey)));

        String requestHash = IdempotencyHash.compute(showId, seats);
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new DeclineException(HttpStatus.CONFLICT, "idempotency_key_reuse");
        }

        try {
            return objectMapper.readValue(existing.getResponse(), ReservationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
