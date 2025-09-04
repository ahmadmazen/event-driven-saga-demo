#!/usr/bin/env bash
set -euo pipefail

LOS_DB_C="postgres-los"
LEDGER_DB_C="postgres-ledger"

echo "Resetting LOS tables..."
docker exec -i "$LOS_DB_C" psql -U los -d los -v ON_ERROR_STOP=1 -c \
  "TRUNCATE TABLE inbox, outbox, participation RESTART IDENTITY CASCADE;"

echo "Resetting Ledger tables & demo wallet balances..."
docker exec -i "$LEDGER_DB_C" psql -U ledger -d ledger -v ON_ERROR_STOP=1 -c \
  "TRUNCATE TABLE inbox, outbox, txn RESTART IDENTITY CASCADE;"
docker exec -i "$LEDGER_DB_C" psql -U ledger -d ledger -v ON_ERROR_STOP=1 -c \
  "UPDATE wallet SET balance = CASE user_id WHEN 101 THEN 10000.00 WHEN 102 THEN 900.00  WHEN 111 THEN 3000.00  WHEN 112 THEN 3000.00 ELSE balance END;"

echo "Done."
