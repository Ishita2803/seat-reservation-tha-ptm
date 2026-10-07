package com.paytmmoney.seatreservation.show;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        Show show = showRepository.save(
                new Show(request.name(), request.pricePaise(), 4, request.seats().size()));

        List<Seat> seats = request.seats().stream()
                .map(label -> new Seat(show.getId(), label))
                .toList();
        seatRepository.saveAll(seats);

        return ShowResponse.from(show, seats);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "show not found"));
        List<Seat> seats = seatRepository.findByShowIdOrderByLabel(id);
        return ShowResponse.from(show, seats);
    }
}
