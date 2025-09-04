# Auto-Invest POC — Diagrams (Mermaid)

Use these Mermaid diagrams directly in your slides or README.

---

## 1) Event-Driven Saga (Sequence)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant LOS as LOS-service
    participant LDB as LOS DB (participation/outbox/inbox)
    participant DebeziumLOS as Debezium Connector (LOS)
    participant Kafka
    participant DebeziumLED as Debezium Connector (Ledger)
    participant Ledger as Ledger-service
    participant LEDB as Ledger DB (wallet/txn/outbox/inbox)

    Client->>LOS: POST /saga/auto-invest
    Note over LOS,LDB: Atomic local transaction:<br/>INSERT participation (PENDING)<br/>INSERT outbox (ParticipationCreated)<br/>COMMIT
    LOS-->>Client: 202 Accepted (sagaId)

    DebeziumLOS-->>Kafka: Publish ParticipationCreated event
    Kafka-->>Ledger: Consumer receives ParticipationCreated

    Note over Ledger,LEDB: Atomic local transaction:<br/>INSERT inbox (messageId deduplication)<br/>IF balance >= amount:<br/>  UPDATE wallet (debit)<br/>  INSERT txn (SUCCESS)<br/>  INSERT outbox (TransactionCompleted)<br/>ELSE:<br/>  INSERT txn (FAILED)<br/>  INSERT outbox (TransactionFailed)<br/>COMMIT

    DebeziumLED-->>Kafka: Publish TransactionCompleted/Failed event
    Kafka-->>LOS: Consumer receives completion event

    Note over LOS,LDB: Atomic local transaction:<br/>INSERT inbox (messageId deduplication)<br/>UPDATE participation -> CONFIRMED/FAILED<br/>COMMIT
```

---

## 2) Sync vs Saga (Flow Comparison)

```mermaid
flowchart LR
subgraph Sync ["❌ Synchronous Flow (Pure HTTP)"]
direction TB
A1[Client sync auto-invest] --> A2[LOS: insert participation PENDING]
A2 --> A3[LOS → HTTP POST /wallet/sync/debit]
A3 -->|HTTP 200| A4[LOS: mark participation CONFIRMED]
A3 -->|HTTP timeout/error| A5[LOS: mark participation FAILED]
A3 -.-> A6[❌ Ledger still processes after timeout]
A6 -.-> A7[❌ Orphaned transaction: Ledger SUCCESS, LOS FAILED]
A5 -.-> A8[❌ No retry mechanism]
end

subgraph Saga ["✅ Saga Flow (Event-Driven)"]
direction TB
B1[Client saga auto-invest] --> B2[LOS atomic tx: participation PENDING + outbox]
B2 --> B3[Debezium → Kafka ParticipationCreated]
B3 --> B4[Ledger consumes + inbox deduplication]
B4 --> B5[Ledger atomic tx: wallet/txn + outbox]
B5 --> B6[Debezium → Kafka TransactionCompleted/Failed]
B6 --> B7[LOS consumes + inbox deduplication]
B7 --> B8[LOS: update participation CONFIRMED/FAILED]
B8 --> B9[✅ Perfect consistency: counters match]
end

classDef bad fill:#ffe6e6,stroke:#ff4d4d,color:#333
classDef good fill:#e6ffe6,stroke:#4dff4d,color:#333

class A6,A7,A8 bad
class B9 good
```

---

## 3) Architecture Overview

```mermaid
graph TB
    subgraph "LOS Service"
        LOS[LOS-service<br/>Spring Boot<br/>:8081]
        LOSDB[(LOS Database<br/>participation, outbox, inbox)]
        LOS --- LOSDB
    end

    subgraph "Ledger Service"
        LED[Ledger-service<br/>Spring Boot<br/>:8090]
        LEDDB[(Ledger Database<br/>wallet, txn, outbox, inbox)]
        LED --- LEDDB
    end

subgraph "Event Infrastructure"
DEB1[Debezium<br/>LOS Connector<br/>outbox → kafka]
DEB2[Debezium<br/>Ledger Connector<br/>outbox → kafka]
KAFKA[Kafka<br/>Topics:<br/>• ParticipationCreated<br/>• TransactionCompleted<br/>• TransactionFailed]
end

Client[Client Application] --> LOS

LOSDB -.->|CDC| DEB1
LEDDB -.->|CDC| DEB2

DEB1 -->|Publish| KAFKA
DEB2 -->|Publish| KAFKA

KAFKA -->|Consume| LED
KAFKA -->|Consume| LOS

classDef service fill:#e1f5fe,stroke:#0277bd,color:#000
classDef database fill:#f3e5f5,stroke:#7b1fa2,color:#000
classDef infrastructure fill:#fff3e0,stroke:#ef6c00,color:#000

class LOS,LED service
class LOSDB,LEDDB database
class DEB1,DEB2,KAFKA infrastructure
```
