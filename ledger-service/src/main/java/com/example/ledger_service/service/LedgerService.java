package com.example.ledger_service.service;


import com.example.ledger_service.model.OutboxEvent;
import com.example.ledger_service.model.Txn;
import com.example.ledger_service.repo.OutboxRepo;
import com.example.ledger_service.repo.TxnRepo;
import com.example.ledger_service.repo.WalletRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {
    private final WalletRepo wallets;
    private final TxnRepo txns;
    private final OutboxRepo outbox;
    private final ObjectMapper M = new ObjectMapper();

    @Transactional
    public Map<String, Object> processDebit(UUID sagaId, Long userId, Long participationId, BigDecimal amount, String idem) {
        log.info("Processing debit - sagaId: {}, userId: {}, participationId: {}, amount: {}, idempotencyKey: {}",
                sagaId, userId, participationId, amount, idem);

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
            log.warn("Insufficient funds for userId: {}, required: {}, available: {}",
                    userId, amount, w.getBalance());

            appendOutbox("TransactionFailed", sagaId, participationId, userId + participationId, idem, "INSUFFICIENT_FUNDS");

            var t = new Txn();
            t.setUserId(userId);
            t.setParticipationId(participationId);
            t.setAmount(amount);
            t.setStatus("FAILED");
            t.setIdempotencyKey(idem);
            t = txns.save(t);

            log.info("Transaction failed due to insufficient funds - transactionId: {}", t.getId());
            return Map.of("status", "FAILED", "reason", "INSUFFICIENT_FUNDS");
        }

        // Process successful debit
        BigDecimal oldBalance = w.getBalance();
        w.setBalance(w.getBalance().subtract(amount));
        wallets.save(w);

        log.debug("Updated wallet balance for userId: {} from {} to {}",
                userId, oldBalance, w.getBalance());

        var t = new Txn();
        t.setUserId(userId);
        t.setParticipationId(participationId);
        t.setAmount(amount);
        t.setStatus("SUCCESS");
        t.setIdempotencyKey(idem);
        t = txns.save(t);

        log.info("Transaction saved successfully - transactionId: {}", t.getId());

        appendOutbox("TransactionCompleted", sagaId, participationId, t.getId(), idem, null);

        log.info("Debit processing completed successfully - transactionId: {}, sagaId: {}",
                t.getId(), sagaId);

        return Map.of("status", "SUCCESS", "transactionId", t.getId());
    }

    private void appendOutbox(String type, UUID sagaId, Long participationId, Long txId, String idem, String reason) {
        log.debug("Appending outbox event - type: {}, sagaId: {}, participationId: {}, txId: {}, reason: {}",
                type, sagaId, participationId, txId, reason);

        try {
            // Use HashMap instead of Map.of() to handle null values
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageId", UUID.randomUUID().toString());
            payload.put("sagaId", sagaId.toString());
            payload.put("participationId", participationId);
            payload.put("transactionId", txId);
            payload.put("idempotencyKey", idem);
            payload.put("status", type.equals("TransactionCompleted") ? "SUCCESS" : "FAILED");
            payload.put("reason", reason); // This can be null and HashMap handles it fine

            var e = new OutboxEvent();
            e.setEventType(type);
            e.setAggregateType("Transaction");
            e.setAggregateId(txId);

            String payloadJson = M.writeValueAsString(payload);
            e.setPayload(payloadJson);

            e = outbox.save(e);

            log.debug("Outbox event saved successfully - eventId: {}, type: {}, payload: {}",
                    e.getId(), type, payloadJson);

        } catch (Exception ex) {
            log.error("Failed to append outbox event - type: {}, sagaId: {}, error: {}",
                    type, sagaId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to append outbox event", ex);
        }
    }
}
