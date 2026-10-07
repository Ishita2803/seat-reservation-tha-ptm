package com.paytmmoney.seatreservation.show;

import java.util.List;

public record SeatCounts(long available, long held, long confirmed) {

    static SeatCounts from(List<Seat> seats) {
        long available = seats.stream().filter(s -> s.getStatus().equals("available")).count();
        long held = seats.stream().filter(s -> s.getStatus().equals("held")).count();
        long confirmed = seats.stream().filter(s -> s.getStatus().equals("confirmed")).count();
        return new SeatCounts(available, held, confirmed);
    }
}
