CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    balance_paise BIGINT NOT NULL DEFAULT 0 CHECK (balance_paise >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id)
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    initiated_by_user_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    from_wallet_id UUID NOT NULL REFERENCES wallets(id),
    to_wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'DECLINED')),
    decline_reason VARCHAR(64),
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_transfers_idem UNIQUE (initiated_by_user_id, idempotency_key),
    CONSTRAINT chk_no_self_transfer CHECK (from_wallet_id <> to_wallet_id)
);
CREATE INDEX idx_transfers_from ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to ON transfers(to_wallet_id);
INSERT INTO wallets (user_id, balance_paise) VALUES ('system-treasury', 100000000000);
