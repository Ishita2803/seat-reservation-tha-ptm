package com.paytmmoney.seatreservation.reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.paytmmoney.seatreservation.common.DeclineException;
import com.paytmmoney.seatreservation.show.Seat;
import com.paytmmoney.seatreservation.show.SeatRepository;
import com.paytmmoney.seatreservation.show.ShowRepository;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserShowLockRepository userShowLockRepository;
    private final Duration holdTtl;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            UserShowLockRepository userShowLockRepository,
            @Value("${app.hold-ttl-minutes}") long holdTtlMinutes) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.userShowLockRepository = userShowLockRepository;
        this.holdTtl = Duration.ofMinutes(holdTtlMinutes);
    }

    @Transactional
    public ReservationResponse reserve(Long showId, String userId, ReserveRequest request) {
        List<String> labels = List.copyOf(new LinkedHashSet<>(request.seats()));
        if (labels.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seats required");
        }

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

        return ReservationResponse.from(reservation);
    }
}
