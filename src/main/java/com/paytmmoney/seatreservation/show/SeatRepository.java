package com.paytmmoney.seatreservation.show;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, SeatId> {

    @Query("SELECT s FROM Seat s WHERE s.id.showId = :showId ORDER BY s.id.label")
    List<Seat> findByShowIdOrderByLabel(Long showId);
}
