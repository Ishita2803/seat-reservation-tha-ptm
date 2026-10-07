package com.paytmmoney.seatreservation.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytmmoney.seatreservation.auth.AuthenticatedUser;
import com.paytmmoney.seatreservation.auth.JwtAuthFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{id}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @PathVariable("id") Long showId,
            @RequestBody ReserveRequest request,
            HttpServletRequest httpRequest) {
        var user = (AuthenticatedUser) httpRequest.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        return reservationService.reserve(showId, user.userId(), request);
    }
}
