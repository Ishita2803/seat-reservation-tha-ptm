package com.paytmmoney.seatreservation.show;

public record SeatResponse(String label, String status) {

    static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getLabel(), seat.getStatus());
    }
}
