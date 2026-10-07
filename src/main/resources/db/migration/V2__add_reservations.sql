ALTER TABLE seats ALTER COLUMN user_id TYPE VARCHAR(100);

CREATE TABLE reservations (
    id             BIGSERIAL PRIMARY KEY,
    show_id        BIGINT NOT NULL REFERENCES shows (id),
    user_id        VARCHAR(100) NOT NULL,
    seats          TEXT[] NOT NULL,
    amount_paise   BIGINT NOT NULL CHECK (amount_paise >= 0),
    status         VARCHAR(10) NOT NULL
                   CHECK (status IN ('held', 'confirmed', 'cancelled', 'expired')),
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE seats ADD CONSTRAINT fk_seats_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations (id);
