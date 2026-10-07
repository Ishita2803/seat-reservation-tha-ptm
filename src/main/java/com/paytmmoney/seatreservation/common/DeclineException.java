package com.paytmmoney.seatreservation.common;

import org.springframework.http.HttpStatus;

/** A clean 4xx decline with a stable machine-readable reason, e.g. "seat_taken". */
public class DeclineException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public DeclineException(HttpStatus status, String reason) {
        super(reason);
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
