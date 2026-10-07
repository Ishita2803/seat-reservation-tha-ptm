package com.paytmmoney.seatreservation.show;

import java.time.Instant;

public record SeatResponse(String label, String status) {

    static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getLabel(), seat.effectiveStatus(Instant.now()));
    }
}
