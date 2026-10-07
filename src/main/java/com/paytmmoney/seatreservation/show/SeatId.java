package com.paytmmoney.seatreservation.show;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SeatId implements Serializable {

    @Column(name = "show_id")
    private Long showId;

    @Column(name = "label")
    private String label;

    protected SeatId() {
    }

    public SeatId(Long showId, String label) {
        this.showId = showId;
        this.label = label;
    }

    public Long getShowId() {
        return showId;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeatId other)) return false;
        return Objects.equals(showId, other.showId) && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showId, label);
    }
}
