package com.example.los_service.mq;

import com.example.los_service.model.InboxMessage;
import com.example.los_service.repo.InboxRepo;
import com.example.los_service.service.ParticipationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class LosKafkaConsumer {
    private final ParticipationService svc;
    private final InboxRepo inbox;
    private final ObjectMapper M = new ObjectMapper();

    @KafkaListener(topics = "TransactionCompleted", groupId = "los-service")
    @Transactional
    public void onCompleted(String json) throws Exception {
        Long pid = processMessage(json, "TransactionCompleted");
        if (pid != null) {
            log.info("Marking participation {} as confirmed", pid);
            svc.markConfirmed(pid);
        }
    }

    @KafkaListener(topics = "TransactionFailed", groupId = "los-service")
    @Transactional
    public void onFailed(String json) throws Exception {
        Long pid = processMessage(json, "TransactionFailed");
        if (pid != null) {
            log.info("Marking participation {} as failed", pid);
            svc.markFailed(pid);
        }
    }

    private Long processMessage(String json, String messageType) throws Exception {
        log.info("Received {} message: {}", messageType, json);

        // Parse the outer JSON structure first
        JsonNode outerNode = M.readTree(json);

        // Extract the payload string and parse it
        String payloadString = outerNode.get("payload").asText();
        JsonNode n = M.readTree(payloadString);

        // Check required fields
        if (n.get("messageId") == null) {
            log.error("Missing messageId in payload: {}", json);
            throw new IllegalArgumentException("Missing messageId field");
        }

        if (n.get("participationId") == null) {
            log.error("Missing participationId in payload: {}", json);
            throw new IllegalArgumentException("Missing participationId field");
        }

        UUID id = UUID.fromString(n.get("messageId").asText());
        if (inbox.existsById(id)) {
            log.info("Message already processed, skipping: {}", id);
            return null; // Return null to indicate already processed
        }

        inbox.save(new InboxMessage(id, payloadString, messageType));
        return n.get("participationId").asLong();
    }
}

