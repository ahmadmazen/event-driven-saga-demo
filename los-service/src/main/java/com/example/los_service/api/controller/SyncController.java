package com.example.los_service.api.controller;

import com.example.los_service.api.record.InvestorReq;
import com.example.los_service.model.Participation;
import com.example.los_service.repo.ParticipationRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {
    private final ParticipationRepo repo;
    private final Random rnd = new Random();

    @Value("${app.ledgerBaseUrl}")
    String ledgerBaseUrl;

    // Configure RestClient with SHORT timeout for demo purposes
    private final RestClient client = RestClient.builder()
            .requestFactory(clientHttpRequestFactory())
            .build();

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));  // 2 second connect timeout
        factory.setReadTimeout(Duration.ofSeconds(3));     // 3 second read timeout
        return factory;
    }

    @PostMapping("/auto-invest")
    @Transactional
    public ResponseEntity<?> autoInvest(@RequestBody List<InvestorReq> reqs) {
        log.info("Starting sync auto-invest with {} requests", reqs.size());
        List<Map<String, Object>> results = new ArrayList<>();

        for (var r : dedupe(reqs)) {
            log.debug("Processing sync request for investorId: {}, userId: {}, loanId: {}, amount: {}",
                    r.investorId(), r.userId(), r.loanId(), r.amount());

            var p = new Participation();
            p.setInvestorId(r.investorId());
            p.setUserId(r.userId());
            p.setLoanId(r.loanId());
            p.setAmount(r.amount());
            p.setStatus(Participation.Status.PENDING);
            p = repo.save(p);

            try {
                var requestBody = Map.of(
                        "userId", r.userId(),
                        "participationId", p.getId(),
                        "amount", r.amount().toString(),
                        "idempotencyKey", r.loanId() + ":" + r.investorId()
                );

                log.debug("Calling SYNC ledger service (no outbox) with request: {}", requestBody);

                // Call the PURE SYNC endpoint (no outbox events)
                var res = client.post()
                        .uri(ledgerBaseUrl + "/wallet/sync/debit")  // Changed endpoint
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(String.class);

                // 🚨 BUG: Always mark as CONFIRMED regardless of actual ledger response
                p.setStatus(Participation.Status.CONFIRMED);
                p = repo.save(p);

                log.info("Marking participation {} as CONFIRMED (assuming success)", p.getId());

                results.add(Map.of(
                        "participationId", p.getId(),
                        "investorId", p.getInvestorId(),
                        "status", p.getStatus().toString()
                ));

            } catch (Exception e) {
                log.error("Exception occurred (likely timeout): {}", e.getMessage());

                // 🚨 CRITICAL BUG: Even on timeout exception, sometimes mark as CONFIRMED!
                // This simulates the worst case: timeout but assuming success
                if (e.getMessage().contains("timeout") || e.getMessage().contains("Read timed out")) {
                    if (rnd.nextDouble() < 0.5) { // 50% of timeouts still marked as "success"
                        p.setStatus(Participation.Status.CONFIRMED);
                        log.warn("💥 INCONSISTENCY: Timeout occurred but marking participation {} as CONFIRMED!", p.getId());

                        results.add(Map.of(
                                "participationId", p.getId(),
                                "investorId", p.getInvestorId(),
                                "status", "CONFIRMED",
                                "note", "Marked as success despite timeout!"
                        ));
                    } else {
                        p.setStatus(Participation.Status.FAILED);
                        results.add(Map.of(
                                "participationId", p.getId(),
                                "investorId", p.getInvestorId(),
                                "status", "FAILED",
                                "error", e.getMessage()
                        ));
                    }
                } else {
                    p.setStatus(Participation.Status.FAILED);
                    results.add(Map.of(
                            "participationId", p.getId(),
                            "investorId", p.getInvestorId(),
                            "status", "FAILED",
                            "error", e.getMessage()
                    ));
                }
                repo.save(p);
            }
        }

        log.info("Completed sync auto-invest processing");
        return ResponseEntity.ok(Map.of("results", results));
    }

    // 🚨 BUG 8: Batch processing with no compensation
    @PostMapping("/auto-invest-batch")
    @Transactional
    public ResponseEntity<?> autoInvestBatch(@RequestBody List<InvestorReq> reqs) {
        log.info("Starting BATCH sync auto-invest with {} requests", reqs.size());

        List<Long> successfulParticipations = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            for (var r : dedupe(reqs)) {
                var p = new Participation();
                p.setInvestorId(r.investorId());
                p.setUserId(r.userId());
                p.setLoanId(r.loanId());
                p.setAmount(r.amount());
                p.setStatus(Participation.Status.PENDING);
                p = repo.save(p);

                // Call ledger for each participation
                var requestBody = Map.of(
                        "userId", r.userId(),
                        "participationId", p.getId(),
                        "amount", r.amount().toString(),
                        "idempotencyKey", r.loanId() + ":" + r.investorId()
                );

                client.post()
                        .uri(ledgerBaseUrl + "/wallet/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(String.class);

                successfulParticipations.add(p.getId());

                // Simulate failure in middle of batch
                if (successfulParticipations.size() == 2 && rnd.nextDouble() < 0.7) {
                    log.error("💥 BATCH FAILURE after processing {} participations", successfulParticipations.size());
                    throw new RuntimeException("Service crashed mid-batch - no rollback mechanism!");
                }
            }

            // 🚨 BUG 9: Only mark as confirmed if entire batch succeeds
            // But individual ledger calls already happened!
            for (Long pid : successfulParticipations) {
                var p = repo.findById(pid).orElseThrow();
                p.setStatus(Participation.Status.CONFIRMED);
                repo.save(p);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "All participations processed successfully",
                    "count", successfulParticipations.size()
            ));

        } catch (Exception e) {
            log.error("Batch processing failed: {}", e.getMessage());

            // 🚨 BUG 10: No compensation! Some ledger debits succeeded,
            // but we can't rollback those transactions
            errors.add("Batch failed: " + e.getMessage() +
                    ". Successfully processed: " + successfulParticipations.size() +
                    " participations, but they remain PENDING with unknown ledger state!");

            return ResponseEntity.status(500).body(Map.of(
                    "errors", errors,
                    "partialSuccess", successfulParticipations.size(),
                    "orphanedTransactions", "Unknown - manual reconciliation required!"
            ));
        }
    }

    private void simulateNetworkIssues() {
        double rand = rnd.nextDouble();

        if (rand < 0.1) { // 10% chance of timeout
            log.warn("🔥 Simulating network timeout");
            try {
                Thread.sleep(10000); // Simulate long timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Read timeout");
        }

        if (rand < 0.15) { // 5% chance of connection reset
            log.warn("🔥 Simulating connection reset");
            throw new RuntimeException("Connection reset by peer");
        }
    }

    private boolean simulatePartialNetworkFailure() {
        return rnd.nextDouble() < 0.05; // 5% chance
    }

    // 🚨 GUARANTEED INCONSISTENCY: For demo purposes
    @PostMapping("/auto-invest-broken")
    @Transactional
    public ResponseEntity<?> autoInvestBroken(@RequestBody List<InvestorReq> reqs) {
        log.info("Starting INTENTIONALLY BROKEN sync auto-invest with {} requests", reqs.size());
        List<Map<String, Object>> results = new ArrayList<>();

        for (var r : dedupe(reqs)) {
            var p = new Participation();
            p.setInvestorId(r.investorId());
            p.setUserId(r.userId());
            p.setLoanId(r.loanId());
            p.setAmount(r.amount());
            p.setStatus(Participation.Status.PENDING);
            p = repo.save(p);

            try {
                var requestBody = Map.of(
                        "userId", r.userId(),
                        "participationId", p.getId(),
                        "amount", r.amount().toString(),
                        "idempotencyKey", r.loanId() + ":" + r.investorId()
                );

                // Call ledger (this might succeed or fail based on business logic)
                client.post()
                        .uri(ledgerBaseUrl + "/wallet/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .toEntity(String.class);

                // 🚨 CRITICAL BUG: ALWAYS mark as CONFIRMED regardless of what ledger did
                p.setStatus(Participation.Status.CONFIRMED);
                p = repo.save(p);

                log.warn("💥 FORCED INCONSISTENCY: Marked participation {} as CONFIRMED without verifying ledger result", p.getId());

                results.add(Map.of(
                        "participationId", p.getId(),
                        "investorId", p.getInvestorId(),
                        "status", "CONFIRMED"
                ));

            } catch (Exception e) {
                // Even on exception, mark as CONFIRMED (worst possible behavior)
                p.setStatus(Participation.Status.CONFIRMED);
                repo.save(p);

                log.warn("💥 FORCED INCONSISTENCY: Exception occurred but still marked {} as CONFIRMED!", p.getId());

                results.add(Map.of(
                        "participationId", p.getId(),
                        "investorId", p.getInvestorId(),
                        "status", "CONFIRMED",
                        "note", "Marked as success despite exception!"
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "results", results,
                "warning", "This endpoint INTENTIONALLY creates inconsistencies for demo purposes"
        ));
    }

    private List<InvestorReq> dedupe(List<InvestorReq> list) {
        Map<Long, InvestorReq> deduped = new HashMap<>();
        for (InvestorReq req : list) {
            deduped.putIfAbsent(req.investorId(), req);
        }
        return new ArrayList<>(deduped.values());
    }
}

