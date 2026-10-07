package com.paytmmoney.seatreservation.show;

import java.time.Instant;
import java.util.List;

public record SeatCounts(long available, long held, long confirmed) {

    static SeatCounts from(List<Seat> seats) {
        Instant now = Instant.now();
        long available = seats.stream().filter(s -> s.effectiveStatus(now).equals("available")).count();
        long held = seats.stream().filter(s -> s.effectiveStatus(now).equals("held")).count();
        long confirmed = seats.stream().filter(s -> s.effectiveStatus(now).equals("confirmed")).count();
        return new SeatCounts(available, held, confirmed);
    }
}
