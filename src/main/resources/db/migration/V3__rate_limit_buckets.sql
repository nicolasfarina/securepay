CREATE TABLE rate_limit_buckets (
    key_hash VARCHAR(64) PRIMARY KEY,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    refill_period_ms BIGINT NOT NULL CHECK (refill_period_ms > 0),
    tokens DOUBLE PRECISION NOT NULL CHECK (tokens >= 0),
    last_refill_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_access_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_rate_limit_buckets_last_access
    ON rate_limit_buckets(last_access_at);
