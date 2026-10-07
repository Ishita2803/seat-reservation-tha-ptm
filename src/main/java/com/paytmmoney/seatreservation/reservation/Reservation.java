package com.paytmmoney.seatreservation.reservation;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> seats;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    public Reservation(Long showId, String userId, List<String> seats, long amountPaise, Instant expiresAt) {
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.amountPaise = amountPaise;
        this.status = "held";
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getSeats() {
        return seats;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return status.equals("held") && expiresAt != null && expiresAt.isBefore(now);
    }

    public void confirm() {
        this.status = "confirmed";
        this.expiresAt = null;
    }

    public void cancel() {
        this.status = "cancelled";
        this.expiresAt = null;
    }

    public void expire() {
        this.status = "expired";
        this.expiresAt = null;
    }
}
