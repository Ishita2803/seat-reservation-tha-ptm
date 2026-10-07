package com.paytmmoney.seatreservation.show;

import java.time.Instant;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "seats")
public class Seat implements Persistable<SeatId> {

    @EmbeddedId
    private SeatId id;

    /**
     * Seats always have an assigned (show_id, label) id, so Spring Data would otherwise treat
     * save() as an update (merge) instead of an insert. True only right after construction,
     * so batched inserts on show creation stay plain inserts.
     */
    @Transient
    private boolean isNew = false;

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
        this.isNew = true;
    }

    @Override
    public SeatId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    void onPersisted() {
        isNew = false;
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

    public void confirm() {
        this.status = "confirmed";
        this.expiresAt = null;
    }

    /** Back to available: used by cancel, and by lazy/swept expiry. */
    public void release() {
        this.status = "available";
        this.reservationId = null;
        this.userId = null;
        this.expiresAt = null;
    }
}
