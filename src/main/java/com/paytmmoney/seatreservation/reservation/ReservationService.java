package com.paytmmoney.seatreservation.reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytmmoney.seatreservation.common.DeclineException;
import com.paytmmoney.seatreservation.show.Seat;
import com.paytmmoney.seatreservation.show.SeatRepository;
import com.paytmmoney.seatreservation.show.ShowRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserShowLockRepository userShowLockRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Duration holdTtl;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            UserShowLockRepository userShowLockRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.hold-ttl-minutes}") long holdTtlMinutes) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.userShowLockRepository = userShowLockRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.holdTtl = Duration.ofMinutes(holdTtlMinutes);
    }

    /**
     * Throws {@link org.springframework.dao.DataIntegrityViolationException} on a duplicate
     * idempotency key; the caller (a non-transactional orchestrator) must resolve the replay
     * in a fresh transaction, since this transaction and its persistence context are now dead.
     */
    @Transactional
    public ReservationResponse reserve(Long showId, String userId, String idempotencyKey, ReserveRequest request) {
        List<String> labels = List.copyOf(new LinkedHashSet<>(request.seats()));
        if (labels.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seats required");
        }

        String requestHash = IdempotencyHash.compute(showId, request.seats());
        var replay = findReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            return doReserve(showId, userId, idempotencyKey, requestHash, labels, request);
        } catch (DeclineException e) {
            // A concurrent request with the same key may have committed while we were
            // blocked on the seat lock; defer to it instead of declining this one.
            var lateReplay = findReplay(userId, idempotencyKey, requestHash);
            if (lateReplay.isPresent()) {
                return lateReplay.get();
            }
            throw e;
        }
    }

    private ReservationResponse doReserve(
            Long showId, String userId, String idempotencyKey, String requestHash, List<String> labels,
            ReserveRequest request) {
        var show = showRepository.findById(showId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "show not found"));

        List<Seat> lockedSeats = seatRepository.lockSeatsForUpdate(showId, labels);
        if (lockedSeats.size() != labels.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "seat not found");
        }

        Instant now = Instant.now();
        boolean anyTaken = lockedSeats.stream().anyMatch(s -> !s.isEffectivelyAvailable(now));
        if (anyTaken) {
            throw new DeclineException(HttpStatus.CONFLICT, "seat_taken");
        }

        userShowLockRepository.ensureRow(showId, userId);
        userShowLockRepository.lockRow(showId, userId);
        long activeForUser = seatRepository.countActiveForUser(showId, userId, now);
        if (activeForUser + labels.size() > show.getPerUserLimit()) {
            throw new DeclineException(HttpStatus.CONFLICT, "per_user_limit");
        }

        Instant expiresAt = now.plus(holdTtl);
        long amountPaise = show.getPricePaise() * labels.size();
        Reservation reservation = reservationRepository.save(
                new Reservation(showId, userId, labels, amountPaise, expiresAt));

        lockedSeats.forEach(seat -> seat.hold(reservation.getId(), userId, expiresAt));

        ReservationResponse response = ReservationResponse.from(reservation);
        idempotencyKeyRepository.saveAndFlush(
                new IdempotencyKey(userId, idempotencyKey, requestHash, reservation.getId(), toJson(response)));

        meterRegistry.counter("reservations_held_total").increment();
        return response;
    }

    @Transactional
    public ReservationResponse confirm(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.lockById(reservationId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "reservation not found"));

        if (!reservation.getStatus().equals("held")) {
            throw new DeclineException(HttpStatus.CONFLICT, "reservation_not_held");
        }

        Instant now = Instant.now();
        if (reservation.isExpired(now)) {
            releaseSeats(reservation);
            reservation.expire();
            meterRegistry.counter("holds_expired_total").increment();
            throw new DeclineException(HttpStatus.CONFLICT, "hold_expired");
        }

        seatRepository.lockSeatsForUpdate(reservation.getShowId(), reservation.getSeats())
                .forEach(Seat::confirm);
        reservation.confirm();

        meterRegistry.counter("reservations_confirmed_total").increment();
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse cancel(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.lockById(reservationId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "reservation not found"));

        if (!(reservation.getStatus().equals("held") || reservation.getStatus().equals("confirmed"))) {
            throw new DeclineException(HttpStatus.CONFLICT, "reservation_not_active");
        }

        releaseSeats(reservation);
        reservation.cancel();

        return ReservationResponse.from(reservation);
    }

    private void releaseSeats(Reservation reservation) {
        seatRepository.lockSeatsForUpdate(reservation.getShowId(), reservation.getSeats())
                .forEach(Seat::release);
    }

    /** Empty when no key is stored yet; throws DeclineException when the key is reused with a different body. */
    private Optional<ReservationResponse> findReplay(String userId, String idempotencyKey, String requestHash) {
        var existing = idempotencyKeyRepository.findById(new IdempotencyKeyId(userId, idempotencyKey));
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        if (!existing.get().getRequestHash().equals(requestHash)) {
            throw new DeclineException(HttpStatus.CONFLICT, "idempotency_key_reuse");
        }
        meterRegistry.counter("reservations_idempotent_replay_total").increment();
        return Optional.of(fromJson(existing.get().getResponse()));
    }

    private String toJson(ReservationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReservationResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, ReservationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
