package com.paytmmoney.seatreservation.reservation;

import java.util.List;

public record ReserveRequest(List<String> seats, String idempotencyKey) {
}
