CREATE TABLE shows (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    price_paise     BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit  INT NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    total_seats     INT NOT NULL CHECK (total_seats > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seats (
    show_id         BIGINT NOT NULL REFERENCES shows (id),
    label           VARCHAR(20) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'available'
                    CHECK (status IN ('available', 'held', 'confirmed')),
    reservation_id  BIGINT,
    user_id         BIGINT,
    expires_at      TIMESTAMPTZ,
    PRIMARY KEY (show_id, label)
);
