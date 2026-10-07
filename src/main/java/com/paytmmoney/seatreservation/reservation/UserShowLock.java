package com.paytmmoney.seatreservation.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

/** Per-user mutex row for a show: serialises one user's concurrent reserve attempts. */
@Entity
@Table(name = "user_show_locks")
public class UserShowLock {

    @EmbeddedId
    private UserShowLockId id;

    protected UserShowLock() {
    }
}
