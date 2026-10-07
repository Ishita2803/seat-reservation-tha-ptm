package com.paytmmoney.seatreservation.show;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, SeatId> {

    @Query("SELECT s FROM Seat s WHERE s.id.showId = :showId ORDER BY s.id.label")
    List<Seat> findByShowIdOrderByLabel(Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id.showId = :showId AND s.id.label IN :labels ORDER BY s.id.label")
    List<Seat> lockSeatsForUpdate(Long showId, List<String> labels);
}
