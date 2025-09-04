package com.example.ledger_service.repo;



import com.example.ledger_service.model.Wallet;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
@Repository
public interface WalletRepo extends JpaRepository<Wallet, Long> {}

