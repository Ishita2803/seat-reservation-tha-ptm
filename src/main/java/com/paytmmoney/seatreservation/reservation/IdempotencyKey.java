package com.paytmmoney.seatreservation.reservation;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Rows are only ever inserted, never updated, so a duplicate key must fail the unique
 * constraint rather than silently merge into the existing row. Persistable forces that:
 * without it, Spring Data treats an entity with an assigned (non-generated) id as existing
 * and does a select-then-insert-or-update instead of a plain insert.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey implements Persistable<IdempotencyKeyId> {

    @EmbeddedId
    private IdempotencyKeyId id;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String response;

    @Transient
    private boolean isNew = false;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String userId, String key, String requestHash, Long reservationId, String response) {
        this.id = new IdempotencyKeyId(userId, key);
        this.requestHash = requestHash;
        this.reservationId = reservationId;
        this.response = response;
        this.isNew = true;
    }

    @Override
    public IdempotencyKeyId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponse() {
        return response;
    }
}
