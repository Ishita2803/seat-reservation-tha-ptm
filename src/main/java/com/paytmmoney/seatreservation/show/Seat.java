package com.paytmmoney.seatreservation.show;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

@Entity
@Table(name = "seats")
public class Seat {

    @EmbeddedId
    private SeatId id;

    @Column(nullable = false)
    private String status;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Seat() {
    }

    public Seat(Long showId, String label) {
        this.id = new SeatId(showId, label);
        this.status = "available";
    }

    public String getLabel() {
        return id.getLabel();
    }

    /** Available right now, or held but past its expiry (lazy expiry). */
    public boolean isEffectivelyAvailable(Instant now) {
        return effectiveStatus(now).equals("available");
    }

    /** Status as of now, treating an expired hold as available before the sweeper clears it. */
    public String effectiveStatus(Instant now) {
        if (status.equals("held") && expiresAt != null && expiresAt.isBefore(now)) {
            return "available";
        }
        return status;
    }

    public void hold(Long reservationId, String userId, Instant expiresAt) {
        this.status = "held";
        this.reservationId = reservationId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
