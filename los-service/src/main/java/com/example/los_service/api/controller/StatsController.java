package com.example.los_service.api.controller;


import com.example.los_service.model.Participation;
import com.example.los_service.repo.InboxRepo;
import com.example.los_service.repo.OutboxRepo;
import com.example.los_service.repo.ParticipationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final ParticipationRepo participationRepo;
    private final OutboxRepo outboxRepo;
    private final InboxRepo inboxRepo;

    @GetMapping("/consistency")
    public Map<String, Object> getConsistencyStats() {
        long pending = participationRepo.countByStatus(Participation.Status.PENDING);
        long confirmed = participationRepo.countByStatus(Participation.Status.CONFIRMED);
        long failed = participationRepo.countByStatus(Participation.Status.FAILED);
        long total = participationRepo.count();

        return Map.of(
                "pending", pending,
                "confirmed", confirmed,
                "failed", failed,
                "total", total,
                "timestamp", Instant.now()
        );
    }

    @GetMapping("/outbox")
    public Map<String, Object> getOutboxStats() {
        long newEvents = outboxRepo.countByStatus("NEW");
        long publishedEvents = outboxRepo.countByStatus("PUBLISHED");
        long total = outboxRepo.count();

        return Map.of(
                "new", newEvents,
                "published", publishedEvents,
                "total", total,
                "timestamp", Instant.now()
        );
    }

    @GetMapping("/inbox")
    public Map<String, Object> getInboxStats() {
        long totalMessages = inboxRepo.count();

        return Map.of(
                "processed", totalMessages,
                "timestamp", Instant.now()
        );
    }

    // Show orphaned transactions (LOS FAILED but Ledger SUCCESS)
    @GetMapping("/orphans")
    public ResponseEntity<?> findOrphanedTransactions() {
        // This would require cross-service querying in real implementation
        // For demo purposes, return a placeholder
        return ResponseEntity.ok(Map.of(
                "message", "Check LOS vs Ledger stats for mismatches",
                "recommendation", "Compare /stats/consistency vs ledger /stats/ledger"
        ));
    }
}
