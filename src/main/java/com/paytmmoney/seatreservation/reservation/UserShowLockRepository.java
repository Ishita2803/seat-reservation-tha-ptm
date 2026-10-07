package com.paytmmoney.seatreservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserShowLockRepository extends JpaRepository<UserShowLock, UserShowLockId> {

    @Modifying
    @Query(value = "INSERT INTO user_show_locks (show_id, user_id) VALUES (:showId, :userId) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensureRow(Long showId, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserShowLock u WHERE u.id.showId = :showId AND u.id.userId = :userId")
    UserShowLock lockRow(Long showId, String userId);
}
