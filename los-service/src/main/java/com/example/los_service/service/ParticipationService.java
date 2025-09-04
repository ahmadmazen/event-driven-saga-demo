package com.example.los_service.service;


import com.example.los_service.api.record.InvestorReq;
import com.example.los_service.model.OutboxEvent;
import com.example.los_service.model.Participation;
import com.example.los_service.model.Participation.Status;
import com.example.los_service.repo.OutboxRepo;
import com.example.los_service.repo.ParticipationRepo;
import com.example.los_service.util.Jsons;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParticipationService {
    private final ParticipationRepo repo;
    private final OutboxRepo outbox;
    private final ObjectMapper M = new ObjectMapper();


    @Transactional
    public Participation createPending(InvestorReq r) {
        var p = new Participation();
        p.setInvestorId(r.investorId());
        p.setUserId(r.userId());
        p.setLoanId(r.loanId());
        p.setAmount(r.amount());
        p.setStatus(Status.PENDING);
        return repo.save(p);
    }

    @Transactional
    public void appendOutboxParticipationCreated(Participation p, UUID sagaId) throws JsonProcessingException {
        var payload = Map.of(
                "messageId", UUID.randomUUID().toString(),
                "sagaId", sagaId.toString(),
                "participationId", p.getId(),
                "investorId", p.getInvestorId(),
                "userId", p.getUserId(),
                "loanId", p.getLoanId(),
                "amount", p.getAmount(),
                "idempotencyKey", p.getLoanId() + ":" + p.getInvestorId()
        );
        outbox.save(OutboxEvent.builder()
                .eventType("ParticipationCreated")
                .aggregateType("Participation")
                .aggregateId(p.getId())
                .payload(M.writeValueAsString(payload))
                .status("NEW")
                .attemptCount(0)
                .build());

        var e = outbox.findById(outbox.getReferenceById(outbox.count()).getId()); // simplified; or use returned entity
    }

    @Transactional
    public void markConfirmed(Long id) {
        repo.findById(id).ifPresent(p -> {
            p.setStatus(Status.CONFIRMED);
        });
    }

    @Transactional
    public void markFailed(Long id) {
        repo.findById(id).ifPresent(p -> {
            p.setStatus(Status.FAILED);
        });
    }
}

