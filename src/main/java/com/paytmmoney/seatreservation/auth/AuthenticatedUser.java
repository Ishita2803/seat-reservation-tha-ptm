package com.paytmmoney.seatreservation.auth;

public record AuthenticatedUser(String userId, boolean admin) {
}
