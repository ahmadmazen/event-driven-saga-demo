//package com.example.ledger_service.controller;
//
//import de.codecentric.spring.boot.chaos.monkey.configuration.AssaultProperties;
//import de.codecentric.spring.boot.chaos.monkey.configuration.ChaosMonkeyProperties;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/chaos")
//public class ChaosController {
//
//    @Autowired
//    private ChaosMonkeyProperties chaosMonkeyProperties;
//
//    @Autowired
//    private AssaultProperties assaultProperties;
//
//    @PostMapping("/enable")
//    public ResponseEntity<String> enableChaos() {
//        chaosMonkeyProperties.setEnabled(true);
//        return ResponseEntity.ok("Chaos Monkey enabled!");
//    }
//
//    @PostMapping("/disable")
//    public ResponseEntity<String> disableChaos() {
//        chaosMonkeyProperties.setEnabled(false);
//        return ResponseEntity.ok("Chaos Monkey disabled!");
//    }
//
//    @PostMapping("/configure")
//    public ResponseEntity<String> configureChaos(@RequestBody ChaosConfig config) {
//        assaultProperties.setLevel(config.level);
//        assaultProperties.setLatencyActive(config.latencyActive);
//        assaultProperties.setExceptionsActive(config.exceptionsActive);
//        assaultProperties.setLatencyRangeStart(config.latencyStart);
//        assaultProperties.setLatencyRangeEnd(config.latencyEnd);
//
//        return ResponseEntity.ok("Chaos configuration updated!");
//    }
//
//    @GetMapping("/status")
//    public ResponseEntity<Map<String, Object>> getChaosStatus() {
//        Map<String, Object> status = new HashMap<>();
//        status.put("enabled", chaosMonkeyProperties.isEnabled());
//        status.put("level", assaultProperties.getLevel());
//        status.put("latencyActive", assaultProperties.isLatencyActive());
//        status.put("exceptionsActive", assaultProperties.isExceptionsActive());
//        status.put("latencyRange", assaultProperties.getLatencyRangeStart() + "-" + assaultProperties.getLatencyRangeEnd() + "ms");
//
//        return ResponseEntity.ok(status);
//    }
//
//    public static class ChaosConfig {
//        public int level;
//        public boolean latencyActive;
//        public boolean exceptionsActive;
//        public int latencyStart;
//        public int latencyEnd;
//    }
//}
//
