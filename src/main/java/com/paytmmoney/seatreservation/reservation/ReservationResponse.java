package com.paytmmoney.seatreservation.reservation;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long showId,
        String userId,
        List<String> seats,
        long amountPaise,
        String status,
        Instant expiresAt) {

    static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getShowId(), r.getUserId(), r.getSeats(), r.getAmountPaise(), r.getStatus(), r.getExpiresAt());
    }
}
