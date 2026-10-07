package com.paytmmoney.seatreservation.show;

import java.util.List;

public record CreateShowRequest(String name, List<String> seats, long pricePaise) {
}
