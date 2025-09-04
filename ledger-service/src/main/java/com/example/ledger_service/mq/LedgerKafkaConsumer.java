package com.example.ledger_service.mq;

import com.example.ledger_service.model.InboxMessage;
import com.example.ledger_service.repo.InboxRepo;
import com.example.ledger_service.service.LedgerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerKafkaConsumer {
    private final LedgerService service;
    private final InboxRepo inbox;
    private final ObjectMapper M = new ObjectMapper();

    @KafkaListener(topics = "ParticipationCreated", groupId = "ledger-service", errorHandler = "kafkaErrorHandler")
    @Transactional
    public void onParticipationCreated(String json) throws Exception {
        log.info("onParticipationCreated >> payload: {}", json);

        // Parse the outer JSON structure first
        JsonNode outerNode = M.readTree(json);

        // Extract the payload string and parse it
        String payloadString = outerNode.get("payload").asText();
        JsonNode n = M.readTree(payloadString);

        // Validate required fields
        if (n.get("messageId") == null) {
            log.error("Missing messageId in payload: {}", json);
            throw new IllegalArgumentException("Missing messageId");
        }

        UUID mid = UUID.fromString(n.get("messageId").asText());
        if (inbox.existsById(mid)) {
            log.info("Message already processed, skipping: {}", mid);
            return;
        }

        inbox.save(new InboxMessage(mid, payloadString, "ParticipationCreated"));

        UUID sagaId = UUID.fromString(n.get("sagaId").asText());
        Long participationId = n.get("participationId").asLong();
        Long userId = n.get("userId").asLong();
        String idem = n.get("idempotencyKey").asText();
        BigDecimal amount = new BigDecimal(n.get("amount").asText());
        service.processDebit(sagaId, userId, participationId, amount, idem);
    }
}