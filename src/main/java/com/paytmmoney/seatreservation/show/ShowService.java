package com.paytmmoney.seatreservation.show;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final MeterRegistry meterRegistry;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository, MeterRegistry meterRegistry) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        Show show = showRepository.save(
                new Show(request.name(), request.pricePaise(), 4, request.seats().size()));

        List<Seat> seats = request.seats().stream()
                .map(label -> new Seat(show.getId(), label))
                .toList();
        seatRepository.saveAll(seats);

        registerSeatGauges(show.getId());

        return ShowResponse.from(show, seats);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "show not found"));
        List<Seat> seats = seatRepository.findByShowIdOrderByLabel(id);
        return ShowResponse.from(show, seats);
    }

    /** Reused by GET /shows/{id} and the per-show gauges, so both read the same effective state. */
    SeatCounts effectiveCounts(Long showId) {
        return SeatCounts.from(seatRepository.findByShowIdOrderByLabel(showId));
    }

    private void registerSeatGauges(Long showId) {
        String tag = showId.toString();
        Gauge.builder("seats_available", showId, id -> effectiveCounts(id).available())
                .tag("show", tag).register(meterRegistry);
        Gauge.builder("seats_held", showId, id -> effectiveCounts(id).held())
                .tag("show", tag).register(meterRegistry);
        Gauge.builder("seats_confirmed", showId, id -> effectiveCounts(id).confirmed())
                .tag("show", tag).register(meterRegistry);
    }
}
