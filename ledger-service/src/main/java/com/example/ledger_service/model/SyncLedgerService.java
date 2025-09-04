package com.example.ledger_service.model;

import com.example.ledger_service.repo.TxnRepo;
import com.example.ledger_service.repo.WalletRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncLedgerService {
    private final WalletRepo wallets;
    private final TxnRepo txns;
    // Note: NO OutboxRepo - this is pure sync!

    @Transactional
    public Map<String, Object> processDebitSync(Long userId, Long participationId, BigDecimal amount, String idem) {
        log.info("Processing SYNC debit - userId: {}, participationId: {}, amount: {}, idempotencyKey: {}",
                userId, participationId, amount, idem);

        // Check for duplicate transaction
        if (txns.existsByIdempotencyKey(idem)) {
            log.warn("Duplicate transaction detected for idempotencyKey: {}", idem);
            return Map.of("status", "DUPLICATE");
        }

        // Find wallet
        var w = wallets.findById(userId).orElseThrow(() -> {
            log.error("Wallet not found for userId: {}", userId);
            return new RuntimeException("Wallet not found for userId: " + userId);
        });

        log.debug("Found wallet for userId: {} with balance: {}", userId, w.getBalance());

        // Check insufficient funds
        if (w.getBalance().compareTo(amount) < 0) {
            log.warn("SYNC: Insufficient funds for userId: {}, required: {}, available: {}",
                    userId, amount, w.getBalance());

            // NO OUTBOX EVENT - just save transaction and return
            var t = new Txn();
            t.setUserId(userId);
            t.setParticipationId(participationId);
            t.setAmount(amount);
            t.setStatus("FAILED");
            t.setIdempotencyKey(idem);
            txns.save(t);

            log.info("SYNC: Transaction failed due to insufficient funds - no event published");
            return Map.of("status", "FAILED", "reason", "INSUFFICIENT_FUNDS");
        }

        // Process successful debit
        BigDecimal oldBalance = w.getBalance();
        w.setBalance(w.getBalance().subtract(amount));
        wallets.save(w);

        log.debug("SYNC: Updated wallet balance for userId: {} from {} to {}",
                userId, oldBalance, w.getBalance());

        var t = new Txn();
        t.setUserId(userId);
        t.setParticipationId(participationId);
        t.setAmount(amount);
        t.setStatus("SUCCESS");
        t.setIdempotencyKey(idem);
        t = txns.save(t);

        log.info("SYNC: Transaction saved successfully - transactionId: {} (NO EVENT PUBLISHED)", t.getId());

        // NO OUTBOX EVENT - this is the key difference!
        return Map.of("status", "SUCCESS", "transactionId", t.getId());
    }
}

