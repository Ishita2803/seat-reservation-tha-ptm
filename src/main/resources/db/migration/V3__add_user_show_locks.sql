CREATE TABLE user_show_locks (
    show_id  BIGINT NOT NULL REFERENCES shows (id),
    user_id  VARCHAR(100) NOT NULL,
    PRIMARY KEY (show_id, user_id)
);
