package com.paytmmoney.seatreservation.reservation;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class IdempotencyKeyId implements Serializable {

    @Column(name = "user_id")
    private String userId;

    @Column(name = "key")
    private String key;

    protected IdempotencyKeyId() {
    }

    public IdempotencyKeyId(String userId, String key) {
        this.userId = userId;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKeyId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, key);
    }
}
