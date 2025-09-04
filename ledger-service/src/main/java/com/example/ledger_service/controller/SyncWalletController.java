package com.example.ledger_service.controller;

import com.example.ledger_service.model.SyncLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/wallet/sync")
@RequiredArgsConstructor
public class SyncWalletController {
    private final SyncLedgerService syncLedgerService;
    private final com.example.ledger_service.api.controller.DeterministicChaosController chaosController;

    @PostMapping("/debit")
    @Transactional
    public ResponseEntity<?> debitSync(@RequestBody Map<String, Object> body) throws Exception {

        chaosController.injectChaos();

        Long userId = ((Number) body.get("userId")).longValue();
        Long pid = ((Number) body.get("participationId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String idem = (String) body.get("idempotencyKey");

        log.info("SYNC ENDPOINT: Processing debit without outbox events");
        var res = syncLedgerService.processDebitSync(userId, pid, amount, idem);

        return ResponseEntity.ok(res);
    }
}