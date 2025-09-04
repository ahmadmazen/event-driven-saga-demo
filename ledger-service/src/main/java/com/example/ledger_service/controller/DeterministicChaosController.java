package com.example.ledger_service.api.controller;

import de.codecentric.spring.boot.chaos.monkey.configuration.AssaultProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/chaos")
@Component
public class DeterministicChaosController {
    @Autowired
    private AssaultProperties assaultProperties;
    private boolean chaosEnabled = false;
    private boolean forceLatency = false;
    private boolean forceException = false;
    private int latencyMs = 3000;
    private int failureCount = 0;
    private int maxFailures = 2; // Fail exactly 2 requests then succeed

    // Control endpoints
    @PostMapping("/enable-latency/{delayMs}")
    public ResponseEntity<String> enableLatency(@PathVariable int delayMs) {
        this.chaosEnabled = true;
        this.forceLatency = true;
        this.forceException = false;
        this.latencyMs = delayMs;
        return ResponseEntity.ok("Latency chaos enabled: " + delayMs + "ms delay");
    }

    @PostMapping("/enable-exceptions/{maxFailures}")
    public ResponseEntity<String> enableExceptions(@PathVariable int maxFailures) {
        this.chaosEnabled = true;
        this.forceLatency = false;
        this.forceException = true;
        this.maxFailures = maxFailures;
        this.failureCount = 0;
        return ResponseEntity.ok("Exception chaos enabled: fail " + maxFailures + " requests");
    }

    @PostMapping("/disable")
    public ResponseEntity<String> disableChaos() {
        this.chaosEnabled = false;
        this.forceLatency = false;
        this.forceException = false;
        this.failureCount = 0;
        return ResponseEntity.ok("All chaos disabled");
    }
        @PostMapping("/configure")
    public ResponseEntity<String> configureChaos(@RequestBody ChaosConfig config) {
        assaultProperties.setLevel(config.level);
        assaultProperties.setLatencyActive(config.latencyActive);
        assaultProperties.setExceptionsActive(config.exceptionsActive);
        assaultProperties.setLatencyRangeStart(config.latencyStart);
        assaultProperties.setLatencyRangeEnd(config.latencyEnd);


        return ResponseEntity.ok("Chaos configuration updated!");
    }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "chaosEnabled", chaosEnabled,
                "forceLatency", forceLatency,
                "forceException", forceException,
                "latencyMs", latencyMs,
                "failureCount", failureCount,
                "maxFailures", maxFailures
        ));
    }

    // Chaos injection methods
    public void injectChaos() throws Exception {
        if (!chaosEnabled) return;

        if (forceLatency) {
            Thread.sleep(latencyMs);
        }

        if (forceException && failureCount < maxFailures) {
            failureCount++;
            throw new RuntimeException("Deterministic chaos: simulated failure #" + failureCount);
        }
    }

    public void reset() {
        this.failureCount = 0;
    }
    public static class ChaosConfig {
        public int level;
        public boolean latencyActive;
        public boolean exceptionsActive;
        public int latencyStart;
        public int latencyEnd;
    }
}
