package com.paytmmoney.seatreservation.show;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paytmmoney.seatreservation.auth.AuthenticatedUser;
import com.paytmmoney.seatreservation.auth.JwtAuthFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@RequestBody CreateShowRequest request, HttpServletRequest httpRequest) {
        var user = (AuthenticatedUser) httpRequest.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        if (!user.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin token required");
        }
        return showService.createShow(request);
    }

    @GetMapping("/shows/{id}")
    public ShowResponse getShow(@PathVariable Long id) {
        return showService.getShow(id);
    }
}
