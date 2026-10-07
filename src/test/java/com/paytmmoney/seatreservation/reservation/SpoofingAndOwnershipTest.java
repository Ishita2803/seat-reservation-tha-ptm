package com.paytmmoney.seatreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.paytmmoney.seatreservation.AbstractIntegrationTest;

/** Identity comes from the token, never the body; only the owner may confirm or cancel. */
class SpoofingAndOwnershipTest extends AbstractIntegrationTest {

    @Test
    void spoofedBodyUserIdIsIgnored() {
        var show = createShow(List.of("Z1"), 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String aliceToken = token("alice");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(aliceToken);
        headers.set("Idempotency-Key", "spoof-key-" + System.nanoTime());
        var body = Map.of("seats", List.of("Z1"), "user_id", "mallory");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/shows/" + showId + "/reserve", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().get("user_id")).isEqualTo("alice");
    }

    @Test
    void nonOwnerCannotConfirmOrCancel() {
        var show = createShow(List.of("Z2"), 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String aliceToken = token("alice-owner");
        String eveToken = token("eve-attacker");

        var reserved = reserve(aliceToken, showId, List.of("Z2"), "owner-key-" + System.nanoTime());
        Long reservationId = ((Number) reserved.getBody().get("reservation_id")).longValue();

        var eveConfirm = confirm(eveToken, reservationId);
        assertThat(eveConfirm.getStatusCode().value()).isEqualTo(404);

        HttpHeaders eveHeaders = new HttpHeaders();
        eveHeaders.setBearerAuth(eveToken);
        var eveCancel = restTemplate.exchange(
                "/reservations/" + reservationId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(null, eveHeaders), Map.class);
        assertThat(eveCancel.getStatusCode().value()).isEqualTo(404);

        var aliceConfirm = confirm(aliceToken, reservationId);
        assertThat(aliceConfirm.getStatusCode().value()).isEqualTo(200);
        assertThat(aliceConfirm.getBody().get("status")).isEqualTo("confirmed");
    }
}
