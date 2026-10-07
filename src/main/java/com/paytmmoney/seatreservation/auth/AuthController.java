package com.paytmmoney.seatreservation.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final JwtService jwtService;
    private final String adminSecret;

    public AuthController(JwtService jwtService, @Value("${app.admin-secret}") String adminSecret) {
        this.jwtService = jwtService;
        this.adminSecret = adminSecret;
    }

    @PostMapping("/auth/tokens")
    public TokenResponse mintToken(
            @RequestHeader("X-Admin-Secret") String providedSecret,
            @RequestBody TokenRequest request) {
        if (!adminSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid admin secret");
        }
        return new TokenResponse(jwtService.issue(request.userId(), request.admin()));
    }
}
