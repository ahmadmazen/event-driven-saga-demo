CREATE TABLE IF NOT EXISTS wallet (
  user_id BIGINT PRIMARY KEY,
  balance NUMERIC(12,2) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS txn (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  participation_id BIGINT,
  amount NUMERIC(12,2) NOT NULL,
  status TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbox (
  id BIGSERIAL PRIMARY KEY,
  event_type TEXT NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id BIGINT,
  payload TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'NEW',
  attempt_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  last_attempt_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_ledger_outbox_status ON outbox(status);

CREATE TABLE IF NOT EXISTS inbox (
  message_id UUID PRIMARY KEY,
  payload TEXT NOT NULL,
  handler TEXT NOT NULL,
  received_at TIMESTAMPTZ DEFAULT now()
);

-- demo seed
INSERT INTO wallet (user_id, balance) VALUES (101, 10000.00)
  ON CONFLICT (user_id) DO NOTHING;
INSERT INTO wallet (user_id, balance) VALUES (102, 900.00)
  ON CONFLICT (user_id) DO NOTHING;
