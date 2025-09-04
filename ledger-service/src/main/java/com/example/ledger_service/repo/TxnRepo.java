package com.example.ledger_service.repo;


import com.example.ledger_service.model.Txn;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
@Repository
public interface TxnRepo extends JpaRepository<Txn, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    long countByStatus(String status);
}

