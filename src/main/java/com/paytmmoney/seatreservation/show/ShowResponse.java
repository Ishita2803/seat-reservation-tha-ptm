package com.paytmmoney.seatreservation.show;

import java.util.List;

public record ShowResponse(
        Long id,
        String name,
        long pricePaise,
        int perUserLimit,
        int totalSeats,
        List<SeatResponse> seats,
        SeatCounts counts) {

    static ShowResponse from(Show show, List<Seat> seats) {
        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                seats.stream().map(SeatResponse::from).toList(),
                SeatCounts.from(seats));
    }
}
