# Auto-Invest POC Demo: Sync vs Saga with Chaos Engineering

This guide demonstrates how **chaos engineering** exposes critical flaws in synchronous approaches that **event-driven sagas** handle gracefully.

---

## 🚨 **Scenario 1: Network Timeout Chaos** 

### The Problem
Network latency causes sync calls to timeout, but the downstream service may still process the transaction, creating **orphaned transactions** with no coordination mechanism.

### Setup: Enable Controlled Latency
```bash
# Enable 5-second latency on Ledger (sync client times out after 3 seconds)
curl -X POST http://localhost:8090/chaos/enable-latency/5000

# Verify chaos is active
curl http://localhost:8090/chaos/status
```

### Test 1: Sync Approach (Pure HTTP) - **WILL FAIL**
```bash
# This will timeout after 3 seconds, but ledger processes after 5 seconds
curl -X POST http://localhost:8081/sync/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[
    {"investorId":1,"userId":101,"loanId":1001,"amount":1500.00},
    {"investorId":2,"userId":102,"loanId":1001,"amount":800.00}
  ]'
```

**What Happens:**
- LOS times out after 3 seconds → marks participations as FAILED
- Ledger processes successfully after 5 seconds → no way to notify LOS
- **Result: Orphaned transactions**

### Check the Orphaned Transactions
```bash
# LOS thinks both failed due to timeout
curl http://localhost:8081/stats/consistency
# Expected: {"pending":0,"confirmed":0,"failed":2,"total":2}

# But Ledger shows successful processing  
curl http://localhost:8090/stats/ledger
# Expected: {"success":2,"failed":0,"total":2}

# 💥 MISMATCH: 2 orphaned transactions requiring manual reconciliation
```

### Test 2: Saga Approach (Event-driven) - **WILL SUCCEED**
```bash
# Same business logic with saga pattern
curl -X POST http://localhost:8081/saga/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[
    {"investorId":3,"userId":103,"loanId":2001,"amount":1500.00},
    {"investorId":4,"userId":104,"loanId":2001,"amount":800.00}
  ]'

# Wait for async processing to complete (chaos still active!)
sleep 10
```

**What Happens:**
- LOS writes PENDING + outbox events atomically
- Kafka delivers events despite network latency
- Ledger processes (slowly due to chaos) and publishes completion events
- LOS receives completion events and updates status correctly
- **Result: Perfect consistency despite network issues**

### Verify Perfect Consistency
```bash
# LOS shows correct final states
curl http://localhost:8081/stats/consistency
# Expected: {"pending":0,"confirmed":2,"failed":0,"total":2} (includes previous sync failures)

# Ledger shows all successful
curl http://localhost:8090/stats/ledger  
# Expected: {"success":2,"failed":0,"total":2}

# ✅ NO ORPHANS: Saga pattern handled network latency gracefully
```

### Cleanup
```bash
# Disable chaos for next scenario
curl -X POST http://localhost:8090/chaos/disable
```

---

## 🚨 **Scenario 3: Complete Service Outage**


### The Problem
When downstream services are completely unavailable, sync approaches fail immediately with no retry mechanism, while saga patterns queue operations for later processing.

```bash
# Temporarily stop the ledger service container
docker stop ledger-service

```

### Test 1: Sync Approach - **NO RECOVERY MECHANISM**
```bash
# During "database issues" - sync fails immediately and gives up
curl -X POST http://localhost:8081/sync/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[{"investorId":5,"userId":105,"loanId":3001,"amount":1200.00}]'

# Check immediate failure
curl http://localhost:8081/stats/consistency
# Shows failure with no retry mechanism
```

### Test 2: Saga Approach - **AUTOMATIC RECOVERY**
```bash
# During same "database issues" - saga queues messages for retry
curl -X POST http://localhost:8081/saga/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[{"investorId":6,"userId":106,"loanId":4001,"amount":1200.00}]'

# Messages are queued in Kafka, waiting for service recovery
curl http://localhost:8081/stats/consistency
# Shows PENDING state
```

### Simulate Recovery
```bash
# "Database comes back online"
docker start ledger-service


# Wait for automatic retry
sleep 10

# Check automatic recovery
curl http://localhost:8081/stats/consistency
curl http://localhost:8090/stats/ledger
# Saga automatically processed queued messages after recovery!
```

**Key Insight**: Async messaging provides **temporal decoupling**, while the saga pattern ensures eventual consistency through coordinated event flows."



## 📊 **Final Comparison Summary**

| Scenario | Sync Approach | Saga Pattern | Business Impact |
|----------|---------------|--------------|-----------------|
| **Network Latency** | ❌ Orphaned transactions | ✅ Handles delays gracefully | Sync: Manual reconciliation required |
| **Service Exceptions** | ❌ No retry mechanism | ✅ Automatic retry via Kafka | Sync: Lost revenue from failed transactions |
| **Service Outage** | ❌ Immediate failure | ✅ Queues and retries after recovery | Sync: Requires manual intervention |
| **Data Consistency** | ❌ Frequent mismatches | ✅ Guaranteed eventual consistency | Sync: Audit compliance issues |

---

## 🎯 **Demo Execution Checklist**

### Pre-Demo Setup
```bash
# Clean slate
curl -X POST http://localhost:8090/chaos/disable
curl http://localhost:8081/stats/consistency
curl http://localhost:8090/stats/ledger
```

### Demo Flow
1. **Scenario 1**: Network latency → Sync creates orphans, Saga handles gracefully
2. **Scenario 2**: Service outage → Sync fails completely, Saga queues and recovers

### Key Talking Points
- **Chaos Engineering**: "We're using the same failure simulation techniques as Netflix and Amazon"
- **Production Reality**: "These aren't artificial bugs - these are real failures that happen in production"
- **Business Impact**: "Manual reconciliation costs, customer disputes, and audit issues"
- **Saga Benefits**: "Event-driven coordination eliminates entire classes of distributed system problems"

---

## 📋 **Endpoints Reference**

### Chaos Control
- `POST /chaos/enable-latency/{ms}` — Add network delay
- `POST /chaos/enable-exceptions/{count}` — Fail N requests then succeed
- `POST /chaos/disable` — Disable all chaos
- `GET /chaos/status` — Check current chaos configuration

### Business Operations
- `POST /sync/auto-invest` — Sync approach (pure HTTP, no events)
- `POST /saga/auto-invest` — Saga approach (event-driven coordination)

### Monitoring
- `GET /stats/consistency` — LOS participation status counts
- `GET /stats/ledger` — Ledger transaction status counts

**The key insight**: Chaos engineering proves that distributed systems need **coordination mechanisms** beyond simple HTTP calls. The saga pattern provides this coordination through **event-driven architecture** and **guaranteed message delivery**.