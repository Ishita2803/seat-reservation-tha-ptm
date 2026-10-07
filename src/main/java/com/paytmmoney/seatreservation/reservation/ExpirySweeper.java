package com.paytmmoney.seatreservation.reservation;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paytmmoney.seatreservation.show.SeatRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * State hygiene only: correctness never depends on this running. A seat with an expired held
 * status is already treated as available by the reserve and confirm paths (lazy expiry); this
 * just clears the stale rows so GET /shows/{id} and the metrics gauges read a clean status
 * without recomputing it, and so held reservations get marked expired for the record.
 */
@Component
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final MeterRegistry meterRegistry;

    public ExpirySweeper(
            SeatRepository seatRepository, ReservationRepository reservationRepository, MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.sweep-interval-ms:5000}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        int seatsReleased = seatRepository.releaseExpiredHolds(now);
        int reservationsExpired = reservationRepository.expireHeldReservations(now);
        if (reservationsExpired > 0) {
            meterRegistry.counter("holds_expired_total").increment(reservationsExpired);
        }
        if (seatsReleased > 0 || reservationsExpired > 0) {
            log.info("swept {} expired seat holds, {} expired reservations", seatsReleased, reservationsExpired);
        }
    }
}
