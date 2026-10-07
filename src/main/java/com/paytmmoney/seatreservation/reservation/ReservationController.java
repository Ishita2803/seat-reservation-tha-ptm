package com.paytmmoney.seatreservation.reservation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paytmmoney.seatreservation.auth.AuthenticatedUser;
import com.paytmmoney.seatreservation.auth.JwtAuthFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ReservationController {

    private final ReservationService reservationService;
    private final IdempotencyReplayService idempotencyReplayService;

    public ReservationController(
            ReservationService reservationService, IdempotencyReplayService idempotencyReplayService) {
        this.reservationService = reservationService;
        this.idempotencyReplayService = idempotencyReplayService;
    }

    @PostMapping("/shows/{id}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @PathVariable("id") Long showId,
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
            HttpServletRequest httpRequest) {
        var user = (AuthenticatedUser) httpRequest.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        String idempotencyKey = headerKey != null ? headerKey : request.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotency key required");
        }

        try {
            return reservationService.reserve(showId, user.userId(), idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            return idempotencyReplayService.resolveReplay(user.userId(), idempotencyKey, showId, request.seats());
        }
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationResponse confirm(@PathVariable("id") Long reservationId, HttpServletRequest httpRequest) {
        var user = (AuthenticatedUser) httpRequest.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        return reservationService.confirm(reservationId, user.userId());
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable("id") Long reservationId, HttpServletRequest httpRequest) {
        var user = (AuthenticatedUser) httpRequest.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        return reservationService.cancel(reservationId, user.userId());
    }
}
