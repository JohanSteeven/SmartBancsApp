CREATE TABLE IF NOT EXISTS bancs_sync_staging (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS bancs_etl_errors (
    id UUID PRIMARY KEY,
    batch_id VARCHAR(100) NOT NULL,
    raw_record JSONB NOT NULL,
    error_code VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bancs_sync_tx ON bancs_sync_staging(transaction_id);
CREATE INDEX IF NOT EXISTS idx_bancs_etl_batch ON bancs_etl_errors(batch_id);
