package com.paytmmoney.seatreservation.common;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeclineException.class)
    public ResponseEntity<Map<String, String>> handleDecline(DeclineException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getReason()));
    }
}
