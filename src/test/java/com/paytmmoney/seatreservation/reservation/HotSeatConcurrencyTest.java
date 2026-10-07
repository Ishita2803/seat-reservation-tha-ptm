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

import com.paytmmoney.seatreservation.AbstractIntegrationTest;

/** 500-people-on-one-seat, scaled down to a thread pool: exactly one winner, everyone else a clean decline. */
class HotSeatConcurrencyTest extends AbstractIntegrationTest {

    @Test
    void exactlyOneWinnerForHotSeat() throws Exception {
        int contenders = 60;
        var show = createShow(List.of("A12"), 25000);
        Long showId = ((Number) show.get("id")).longValue();

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                return reserve(token("hotseat-user-" + idx), showId, List.of("A12"), "key-" + idx);
            }));
        }
        start.countDown();

        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        long wins = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long declines = results.stream().filter(r -> r.getStatusCode().value() == 409).count();
        long serverErrors = results.stream().filter(r -> r.getStatusCode().value() >= 500).count();

        assertThat(serverErrors).isZero();
        assertThat(wins).isEqualTo(1);
        assertThat(declines).isEqualTo(contenders - 1);
        results.stream()
                .filter(r -> r.getStatusCode().value() == 409)
                .forEach(r -> assertThat(r.getBody().get("error")).isEqualTo("seat_taken"));

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) getShow(adminToken(), showId).get("counts");
        assertThat(((Number) counts.get("available")).intValue()).isZero();
        assertThat(((Number) counts.get("held")).intValue()).isEqualTo(1);
    }
}
