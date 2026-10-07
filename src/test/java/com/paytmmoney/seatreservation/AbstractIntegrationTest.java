package com.paytmmoney.seatreservation;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.paytmmoney.seatreservation.auth.JwtService;

/**
 * Real Postgres via Testcontainers, never H2 — the locking behaviour under test (FOR UPDATE,
 * unique constraints) must match what runs in production.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JwtService jwtService;

    protected String token(String userId) {
        return jwtService.issue(userId, false);
    }

    protected String adminToken() {
        return jwtService.issue("test-admin", true);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> createShow(List<String> seats, long pricePaise) {
        var body = Map.of("name", "test-show-" + System.nanoTime(), "seats", seats, "price_paise", pricePaise);
        var response = restTemplate.exchange(
                "/shows", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(adminToken())), Map.class);
        return (Map<String, Object>) response.getBody();
    }

    protected ResponseEntity<Map> reserve(String token, Long showId, List<String> seats, String idempotencyKey) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        var body = Map.of("seats", seats);
        return restTemplate.exchange(
                "/shows/" + showId + "/reserve", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    protected ResponseEntity<Map> confirm(String token, Long reservationId) {
        return restTemplate.exchange(
                "/reservations/" + reservationId + "/confirm", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(null, authHeaders(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getShow(String token, Long showId) {
        var response = restTemplate.exchange(
                "/shows/" + showId, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(token)), Map.class);
        return (Map<String, Object>) response.getBody();
    }
}
