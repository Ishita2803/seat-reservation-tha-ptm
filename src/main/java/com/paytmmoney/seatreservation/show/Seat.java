package com.paytmmoney.seatreservation.show;

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

    protected Seat() {
    }

    public Seat(Long showId, String label) {
        this.id = new SeatId(showId, label);
        this.status = "available";
    }

    public String getLabel() {
        return id.getLabel();
    }

    public String getStatus() {
        return status;
    }
}
