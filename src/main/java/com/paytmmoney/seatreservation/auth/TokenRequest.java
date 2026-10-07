package com.paytmmoney.seatreservation.auth;

public record TokenRequest(String userId, boolean admin) {
}
