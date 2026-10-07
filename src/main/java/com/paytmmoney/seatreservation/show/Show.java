package com.paytmmoney.seatreservation.show;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Show() {
    }

    public Show(String name, long pricePaise, int perUserLimit, int totalSeats) {
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.totalSeats = totalSeats;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public int getTotalSeats() {
        return totalSeats;
    }
}
