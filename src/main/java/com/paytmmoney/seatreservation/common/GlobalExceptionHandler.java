package com.paytmmoney.seatreservation.common;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.micrometer.core.instrument.MeterRegistry;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(DeclineException.class)
    public ResponseEntity<Map<String, String>> handleDecline(DeclineException e) {
        meterRegistry.counter("reservations_declined_total", "reason", e.getReason()).increment();
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getReason()));
    }
}
