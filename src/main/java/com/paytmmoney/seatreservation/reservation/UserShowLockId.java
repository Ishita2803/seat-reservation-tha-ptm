package com.paytmmoney.seatreservation.reservation;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserShowLockId implements Serializable {

    @Column(name = "show_id")
    private Long showId;

    @Column(name = "user_id")
    private String userId;

    protected UserShowLockId() {
    }

    public UserShowLockId(Long showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserShowLockId other)) return false;
        return Objects.equals(showId, other.showId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showId, userId);
    }
}
