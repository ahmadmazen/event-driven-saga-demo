package com.example.ledger_service.controller;


import com.example.ledger_service.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final LedgerService service;

    private final com.example.ledger_service.api.controller.DeterministicChaosController chaosController;

    @PostMapping("/debit")
    @Transactional
    public ResponseEntity<?> debit(@RequestBody Map<String, Object> body) throws Exception {

        chaosController.injectChaos();

        Long userId = ((Number) body.get("userId")).longValue();
        Long pid = ((Number) body.get("participationId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String idem = (String) body.get("idempotencyKey");
        var res = service.processDebit(UUID.randomUUID(), userId, pid, amount, idem);
        return ResponseEntity.ok(res);
    }
}

