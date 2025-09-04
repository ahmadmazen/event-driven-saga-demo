package com.example.ledger_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "txn")
@Getter
@Setter
public class Txn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long userId;
    Long participationId;
    BigDecimal amount;
    String status;
    String idempotencyKey;
}

