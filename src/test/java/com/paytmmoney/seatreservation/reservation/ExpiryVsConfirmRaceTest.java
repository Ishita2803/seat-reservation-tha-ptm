package com.paytmmoney.seatreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytmmoney.seatreservation.AbstractIntegrationTest;

/**
 * hold-ttl-minutes forced to 0: every hold is born already expired. A confirm racing the sweeper
 * must never win — the lazy check inside confirm is what actually guarantees this, the sweeper is
 * only hygiene, so this test leaves the sweeper running at its normal cadence alongside confirm.
 */
class ExpiryVsConfirmRaceTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void zeroTtl(DynamicPropertyRegistry registry) {
        registry.add("app.hold-ttl-minutes", () -> "0");
    }

    @Test
    void concurrentConfirmsOnAnExpiredHoldAllDecline() throws Exception {
        var show = createShow(List.of("E1"), 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String userToken = token("expiry-race-user");

        var reserved = reserve(userToken, showId, List.of("E1"), "expiry-key-" + System.nanoTime());
        assertThat(reserved.getStatusCode().value()).isEqualTo(201);
        Long reservationId = ((Number) reserved.getBody().get("reservation_id")).longValue();

        int contenders = 10;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return confirm(userToken, reservationId);
            }));
        }
        start.countDown();

        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(results).allMatch(r -> r.getStatusCode().value() == 409);
        results.forEach(r -> assertThat(r.getBody().get("error")).isEqualTo("hold_expired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) getShow(userToken, showId).get("counts");
        assertThat(((Number) counts.get("confirmed")).intValue()).isZero();
        assertThat(((Number) counts.get("available")).intValue()).isEqualTo(1);
    }
}
