package com.paytmmoney.seatreservation.show;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, SeatId> {

    @Query("SELECT s FROM Seat s WHERE s.id.showId = :showId ORDER BY s.id.label")
    List<Seat> findByShowIdOrderByLabel(Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id.showId = :showId AND s.id.label IN :labels ORDER BY s.id.label")
    List<Seat> lockSeatsForUpdate(Long showId, List<String> labels);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.id.showId = :showId AND s.userId = :userId "
            + "AND (s.status = 'confirmed' OR (s.status = 'held' AND s.expiresAt > :now))")
    long countActiveForUser(Long showId, String userId, Instant now);

    /**
     * The status='held' guard means this can never touch a seat that was just confirmed or
     * cancelled: a bulk UPDATE's WHERE is re-checked against the current committed row once any
     * concurrent lock on it releases, so a seat that changed status in the meantime is skipped.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Seat s SET s.status = 'available', s.reservationId = null, s.userId = null, s.expiresAt = null "
            + "WHERE s.status = 'held' AND s.expiresAt < :now")
    int releaseExpiredHolds(Instant now);
}
