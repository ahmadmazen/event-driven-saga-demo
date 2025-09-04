package com.example.ledger_service.controller;

import com.example.ledger_service.repo.InboxRepo;
import com.example.ledger_service.repo.OutboxRepo;
import com.example.ledger_service.repo.TxnRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final TxnRepo transactionRepo;
    private final OutboxRepo outboxRepo;
    private final InboxRepo inboxRepo;

    @GetMapping("/ledger")
    public Map<String, Object> getLedgerStats() {
        long successCount = transactionRepo.countByStatus("SUCCESS");
        long failedCount = transactionRepo.countByStatus("FAILED");
        long pendingCount = transactionRepo.countByStatus("PENDING");
        long total = transactionRepo.count();

        long outboxNew = outboxRepo.countByStatus("NEW");

        return Map.of(
                "success", successCount,
                "failed", failedCount,
                "pending", pendingCount,
                "total", total,
                "outboxNew", outboxNew,
                "timestamp", Instant.now()
        );
    }

    @GetMapping("/inbox")
    public Map<String, Object> getInboxStats() {
        long processedMessages = inboxRepo.count();

        return Map.of(
                "processed", processedMessages,
                "timestamp", Instant.now()
        );
    }
}
