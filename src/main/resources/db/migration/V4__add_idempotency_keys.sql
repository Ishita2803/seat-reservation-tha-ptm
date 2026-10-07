CREATE TABLE idempotency_keys (
    user_id         VARCHAR(100) NOT NULL,
    key             VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    reservation_id  BIGINT NOT NULL REFERENCES reservations (id),
    response        JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, key)
);
