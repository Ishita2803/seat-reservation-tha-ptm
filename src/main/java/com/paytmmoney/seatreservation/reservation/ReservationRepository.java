package com.paytmmoney.seatreservation.reservation;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> lockById(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = 'expired', r.expiresAt = null "
            + "WHERE r.status = 'held' AND r.expiresAt < :now")
    int expireHeldReservations(Instant now);
}
