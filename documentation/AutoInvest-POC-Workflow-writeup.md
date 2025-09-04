# Auto‑Invest POC — Workflow (Step‑by‑Step)

This document explains, step by step, how the **sync flow** (old, buggy) works and how the **saga flow** (new, event‑driven) fixes the consistency problem using **Outbox + Debezium + Kafka + Inbox + Idempotency**.

---

## Components (at a glance)

- **LOS‑service** (Spring Boot + Postgres)
  - Tables: `participation`, `outbox`, `inbox`
  - Endpoints: `/sync/auto-invest` (old), `/saga/auto-invest` (new), `/stats/consistency`
  - Kafka consumer for `TransactionCompleted` / `TransactionFailed`

- **Ledger‑service** (Spring Boot + Postgres)
  - Tables: `wallet`, `txn`, `outbox`, `inbox`
  - Endpoints: `/wallet/debit` (for sync path and manual tests), `/stats/ledger`
  - Kafka consumer for `ParticipationCreated`

- **Kafka + Debezium**
  - Debezium connectors on **`public.outbox`** in both DBs publish to Kafka topics:
    - LOS → `ParticipationCreated`
    - Ledger → `TransactionCompleted`, `TransactionFailed`

- **Inbox/Outbox**
  - **Outbox** rows are inserted in the *same* DB transaction as domain writes, then Debezium publishes after commit.
  - **Inbox** records processed message IDs for *exactly‑once side‑effects*.

- **Idempotency**
  - Stable key: `idempotency_key = loanId:investorId` (unique in `participation` & `txn`).

---

## A) Sync Flow (Old, Buggy)

1. **Client** calls `POST /sync/auto-invest` on LOS with a list of investor allocations.
2. For each investor (loop in LOS):
   1. Insert `participation` (often directly marked `CONFIRMED` after success path).
   2. Call **Ledger** `/wallet/debit` synchronously.
   3. On exceptions, LOS attempts to revert—but this is **best effort**, not guaranteed.
3. **Failure modes (why it drifts):**
   - If the LOS process crashes *after* the debit succeeds, you can end up with a **transaction but no participation update** (orphaned transaction).
   - If retries happen without idempotency, duplicates can be created on Ledger.
   - LOS and Ledger counters disagree (partial‑commit).

**Result:** Occasional inconsistencies such as “**transaction exists, participation missing**” or LOS shows **CONFIRMED** while Ledger has **FAILED**.

---

## B) Saga Flow (New, Event‑Driven)

> Key idea: **No synchronous remote calls inside transactions.** Each service performs a **local transaction + outbox write**, and **events** drive the next step.

1. **Client** calls `POST /saga/auto-invest` on LOS with investor allocations.
2. For each investor, LOS performs **one local DB transaction**:
   - Insert `participation(status=PENDING)` with a **unique `idempotency_key`**.
   - Insert `outbox` row with `event_type=ParticipationCreated` (payload: `participationId`, `userId`, `loanId`, `amount`, `idempotency_key`, `sagaId`).  
   → **Commit**.
3. **Debezium (LOS)** reads the new `outbox` rows and publishes to Kafka topic **`ParticipationCreated`**.
4. **Ledger** consumes `ParticipationCreated`:
   - Deduplicate with **Ledger `inbox`**; ignore if already processed.
   - In a **single local DB transaction**:
     - If balance < amount → create `txn(status=FAILED)`, add **`TransactionFailed`** to **Ledger `outbox`**.
     - Else subtract from wallet, create `txn(status=SUCCESS)`, add **`TransactionCompleted`** to **Ledger `outbox`**.  
     → **Commit**.
5. **Debezium (Ledger)** publishes those outbox rows to Kafka topics **`TransactionCompleted`** or **`TransactionFailed`**.
6. **LOS** consumes the Ledger events:
   - Deduplicate with **LOS `inbox`**.
   - Update `participation` → `CONFIRMED` on `TransactionCompleted`, `FAILED` on `TransactionFailed`.
7. **Counters align**: LOS `PENDING` drains to `CONFIRMED/FAILED` and exactly matches Ledger `SUCCESS/FAILED`.
8. **Safety properties:**
   - **No orphans:** Transactions only happen *in response to a committed participation*.
   - **Exactly‑once effects:** Inbox tables + unique idempotency keys make retries safe.
   - **Crash‑safe emission:** Debezium only publishes **after** the DB commit (no “emit then crash”).

---

## C) What to highlight in the POC

- **Before:** `/sync/auto-invest` can produce **mismatched counters** and **orphan states**.
- **After:** `/saga/auto-invest` yields **eventual, deterministic consistency** with **zero orphans**.
- **Operational clarity:** Simple stats endpoints show PENDING → CONFIRMED/FAILED drain.
