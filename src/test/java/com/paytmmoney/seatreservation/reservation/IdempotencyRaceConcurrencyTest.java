package com.paytmmoney.seatreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.paytmmoney.seatreservation.AbstractIntegrationTest;

/** N truly-concurrent requests with the identical idempotency key and body must collapse to one reservation. */
class IdempotencyRaceConcurrencyTest extends AbstractIntegrationTest {

    @Test
    void concurrentIdenticalRequestsCollapseToOneReservation() throws Exception {
        int contenders = 10;
        var show = createShow(List.of("X1"), 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String userToken = token("idem-race-user");
        String sharedKey = "race-key-" + System.nanoTime();

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return reserve(userToken, showId, List.of("X1"), sharedKey);
            }));
        }
        start.countDown();

        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(results).allMatch(r -> r.getStatusCode().value() == 201);
        Set<Object> reservationIds = results.stream()
                .map(r -> r.getBody().get("reservation_id"))
                .collect(Collectors.toSet());
        assertThat(reservationIds).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) getShow(userToken, showId).get("counts");
        assertThat(((Number) counts.get("held")).intValue()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentSeatsIsRejected() {
        var show = createShow(List.of("Y1", "Y2"), 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String userToken = token("idem-mismatch-user");
        String key = "mismatch-key-" + System.nanoTime();

        var first = reserve(userToken, showId, List.of("Y1"), key);
        assertThat(first.getStatusCode().value()).isEqualTo(201);

        var second = reserve(userToken, showId, List.of("Y2"), key);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getBody().get("error")).isEqualTo("idempotency_key_reuse");
    }
}
