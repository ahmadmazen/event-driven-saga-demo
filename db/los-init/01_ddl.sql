CREATE TABLE IF NOT EXISTS participation (
  id BIGSERIAL PRIMARY KEY,
  investor_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  loan_id BIGINT NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  status TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbox (
  id BIGSERIAL PRIMARY KEY,
  event_type TEXT NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id BIGINT NOT NULL,
  payload TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'NEW',
  attempt_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  last_attempt_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_los_outbox_status ON outbox(status);

CREATE TABLE IF NOT EXISTS inbox (
  message_id UUID PRIMARY KEY,
  payload TEXT NOT NULL,
  handler TEXT NOT NULL,
  received_at TIMESTAMPTZ DEFAULT now()
);
