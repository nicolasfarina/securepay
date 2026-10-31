CREATE TABLE revoked_tokens (
    token_id_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX revoked_tokens_expires_at_idx ON revoked_tokens (expires_at);
