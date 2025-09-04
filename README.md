# Saga Pattern Microservices POC

Proof of Concept demonstrating distributed transaction patterns in microservices architecture. Compares synchronous HTTP calls vs event-driven saga patterns for maintaining data consistency across services, using chaos engineering to simulate real production failures.

## Problem Statement

In distributed microservices architectures, maintaining data consistency across services is challenging. Traditional synchronous approaches using HTTP calls create several critical issues:

- **Orphaned Transactions**: Network timeouts can leave transactions in inconsistent states
- **No Retry Mechanism**: Failed operations require manual intervention
- **Tight Coupling**: Services become unavailable when dependencies fail
- **Manual Reconciliation**: Inconsistencies require expensive operational overhead

## Solution: Event-Driven Saga Pattern

This POC demonstrates how the saga pattern with event-driven architecture solves these problems through:

- **Reliable Messaging**: Kafka + Debezium ensure guaranteed event delivery
- **Outbox/Inbox Patterns**: Atomic local transactions with distributed coordination
- **Automatic Retry**: Built-in resilience to transient failures
- **Temporal Decoupling**: Services remain available during dependency outages
- **Eventual Consistency**: System self-heals without manual intervention

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   LOS Service   │    │ Event Platform   │    │ Ledger Service  │
│                 │    │                  │    │                 │
│ ┌─────────────┐ │    │  ┌─────────────┐ │    │ ┌─────────────┐ │
│ │Participation│ │    │  │   Kafka     │ │    │ │   Wallet    │ │
│ │   Outbox    │ │◄───┼──┤             ├─┼───►│ │ Transaction │ │
│ │   Inbox     │ │    │  │  Debezium   │ │    │ │   Outbox    │ │
│ └─────────────┘ │    │  └─────────────┘ │    │ │   Inbox     │ │
└─────────────────┘    └──────────────────┘    │ └─────────────┘ │
                                               └─────────────────┘
```

## Technology Stack

- **Backend**: Java 17, Spring Boot 3.x
- **Messaging**: Apache Kafka, Debezium CDC
- **Database**: PostgreSQL
- **Containerization**: Docker, Docker Compose
- **Chaos Engineering**: Spring Boot Chaos Monkey
- **Build Tool**: Maven

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 17+
- Maven 3.6+

### Running the Demo

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/saga-pattern-microservices-poc.git
   cd saga-pattern-microservices-poc
   ```

2. **Start the infrastructure**
   ```bash
   docker-compose up -d kafka postgres debezium
   ```

3. **Build and start services**
   ```bash
   mvn clean package
   docker-compose up -d los-service ledger-service
   ```

4. **Verify services are running**
   ```bash
   curl http://localhost:8081/stats/consistency
   curl http://localhost:8090/stats/ledger
   ```

## Demo Scenarios

### Scenario 1: Network Timeout Chaos

Demonstrates how network latency creates orphaned transactions in sync approaches while saga patterns handle delays gracefully.

```bash
# Enable 5-second latency (sync client times out after 3 seconds)
curl -X POST http://localhost:8090/chaos/enable-latency/5000

# Sync approach - creates orphaned transactions
curl -X POST http://localhost:8081/sync/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[{"investorId":1,"userId":101,"loanId":1001,"amount":1500.00}]'

# Saga approach - handles latency gracefully  
curl -X POST http://localhost:8081/saga/auto-invest \
  -H 'Content-Type: application/json' \
  -d '[{"investorId":2,"userId":102,"loanId":2001,"amount":1500.00}]'

# Compare results
curl http://localhost:8081/stats/consistency
curl http://localhost:8090/stats/ledger
```

### Scenario 2: Service Exception Chaos

Shows how saga patterns retry automatically while sync approaches fail permanently.

```bash
# Enable exceptions for 2 requests then succeed
curl -X POST http://localhost:8090/chaos/enable-exceptions/2

# Test both approaches and observe retry behavior
```

### Scenario 3: Complete Service Outage

Demonstrates saga's ability to queue operations during outages and automatically recover.

```bash
# Simulate service outage
docker pause ledger-service

# Test both approaches
curl -X POST http://localhost:8081/sync/auto-invest -H 'Content-Type: application/json' -d '[{"investorId":11,"userId":111,"loanId":5001,"amount":2000.00}]'

curl -X POST http://localhost:8081/saga/auto-invest -H 'Content-Type: application/json' -d '[{"investorId":12,"userId":112,"loanId":6001,"amount":2000.00}]'

# Simulate recovery
docker unpause ledger-service

# Observe automatic processing of queued messages
sleep 10
curl http://localhost:8081/stats/consistency
```

## API Endpoints

### Business Operations
- `POST /sync/auto-invest` - Synchronous approach (pure HTTP)
- `POST /saga/auto-invest` - Saga approach (event-driven)

### Chaos Engineering
- `POST /chaos/enable-latency/{ms}` - Add network delay
- `POST /chaos/enable-exceptions/{count}` - Fail N requests then succeed
- `POST /chaos/disable` - Disable all chaos
- `GET /chaos/status` - Check chaos configuration

### Monitoring
- `GET /stats/consistency` - LOS participation status counts
- `GET /stats/ledger` - Ledger transaction status counts
- `GET /stats/outbox` - Event publishing statistics
- `GET /stats/inbox` - Message consumption statistics

## Key Insights

### Problems with Synchronous Approach
- **Orphaned Transactions**: Network timeouts leave money debited but participations unmarked
- **No Retry Logic**: Transient failures become permanent failures
- **Blocking Operations**: Service outages cascade to dependent services
- **Manual Reconciliation**: Requires expensive operational overhead

### Benefits of Saga Pattern
- **Guaranteed Consistency**: Eventually consistent through reliable event delivery
- **Automatic Recovery**: Built-in retry and error handling via Kafka
- **Service Isolation**: Failures don't cascade between services
- **Observable**: Full audit trail through event sourcing
- **Scalable**: Asynchronous processing handles load spikes

## Production Considerations

### Monitoring
- Track outbox publishing lag
- Monitor inbox processing rates
- Alert on saga completion timeouts
- Dashboard for cross-service consistency metrics

### Security
- Encrypt sensitive data in events
- Implement event schema validation
- Use Kafka ACLs for topic access control
- Audit trail for financial transactions

### Performance
- Batch event publishing for high throughput
- Partition Kafka topics by tenant/region
- Implement circuit breakers for downstream calls
- Cache frequently accessed reference data

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## References

- [Saga Pattern - Martin Fowler](https://martinfowler.com/articles/patterns-of-distributed-systems/saga.html)
- [Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Documentation](https://debezium.io/documentation/)
- [Chaos Engineering Principles](https://principlesofchaos.org/)

## Contact

For questions or feedback, please open an issue or contact [your-email@domain.com]