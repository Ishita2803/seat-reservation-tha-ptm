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

/** One user firing 10 parallel single-seat reserves against a default per_user_limit of 4. */
class PerUserLimitConcurrencyTest extends AbstractIntegrationTest {

    @Test
    void atMostFourHeldUnderConcurrency() throws Exception {
        List<String> seats = List.of("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10");
        var show = createShow(seats, 1000);
        Long showId = ((Number) show.get("id")).longValue();
        String userToken = token("limit-test-user");

        ExecutorService pool = Executors.newFixedThreadPool(seats.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (String seat : seats) {
            futures.add(pool.submit(() -> {
                start.await();
                return reserve(userToken, showId, List.of(seat), "limit-key-" + seat);
            }));
        }
        start.countDown();

        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        long held = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long declined = results.stream().filter(r -> r.getStatusCode().value() == 409).count();

        assertThat(held).isEqualTo(4);
        assertThat(declined).isEqualTo(6);
        results.stream()
                .filter(r -> r.getStatusCode().value() == 409)
                .forEach(r -> assertThat(r.getBody().get("error")).isEqualTo("per_user_limit"));

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) getShow(userToken, showId).get("counts");
        assertThat(((Number) counts.get("held")).intValue()).isEqualTo(4);
        assertThat(((Number) counts.get("available")).intValue()).isEqualTo(6);
    }
}
