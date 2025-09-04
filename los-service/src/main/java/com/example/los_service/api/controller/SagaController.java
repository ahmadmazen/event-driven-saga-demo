package com.example.los_service.api.controller;


import com.example.los_service.api.record.InvestorReq;
import com.example.los_service.model.OutboxEvent;
import com.example.los_service.model.Participation;
import com.example.los_service.repo.OutboxRepo;
import com.example.los_service.service.ParticipationService;
import com.example.los_service.util.Jsons;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/saga")
@RequiredArgsConstructor
public class SagaController {
    private final ParticipationService svc;
    private final OutboxRepo outbox;
    private final ObjectMapper M = new ObjectMapper();


    @PostMapping("/auto-invest")
    @Transactional
    public ResponseEntity<?> autoInvest(@RequestBody List<InvestorReq> reqs) {
        UUID sagaId = UUID.randomUUID();
        log.info("Starting auto-invest batch with sagaId: {}, requests: {}", sagaId, reqs.size());

        for (var r : reqs.stream().collect(
                java.util.stream.Collectors.toMap(InvestorReq::investorId, it -> it, (a, b) -> a)).values()) {

            log.debug("Processing request for investorId: {}, userId: {}, loanId: {}, amount: {}",
                    r.investorId(), r.userId(), r.loanId(), r.amount());

            Participation p = svc.createPending(r);

            // ⚠️ ISSUE: Amount should be converted to string for JSON consistency
            // The Kafka consumer expects string values, but you're putting BigDecimal directly
            var payload = Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "sagaId", sagaId.toString(),
                    "participationId", p.getId(),
                    "investorId", p.getInvestorId(),
                    "userId", p.getUserId(),
                    "loanId", p.getLoanId(),
                    "amount", p.getAmount().toString(), // 🔧 FIX: Convert to string
                    "idempotencyKey", p.getLoanId() + ":" + p.getInvestorId()
            );

            String payloadJson;
            try {
                payloadJson = M.writeValueAsString(payload);
                log.debug("Created outbox payload: {}", payloadJson);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event - type: {}, sagaId: {}, error: {}",
                        "ParticipationCreated", sagaId, e.getMessage(), e);
                throw new RuntimeException("Failed to serialize outbox event", e);
            }

            outbox.save(OutboxEvent.builder()
                    .eventType("ParticipationCreated")
                    .aggregateType("Participation")
                    .aggregateId(p.getId())
                    .payload(payloadJson)
                    .status("NEW")
                    .attemptCount(0)
                    .build());

            log.info("Created participation {} for investor {} with amount {}",
                    p.getId(), p.getInvestorId(), p.getAmount());
        }

        log.info("Completed auto-invest batch with sagaId: {}", sagaId);
        return ResponseEntity.accepted().body(Map.of("batchId", sagaId.toString()));
    }
}

