package com.example.los_service.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "participation")
@Getter
@Setter
public class Participation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long investorId;
    Long userId;
    Long loanId;
    BigDecimal amount;
    @Enumerated(EnumType.STRING)
    Status status;
    String idempotencyKey;

    @PrePersist
    void pre() {
        if (idempotencyKey == null) idempotencyKey = loanId + ":" + investorId;
    }

    public enum Status {PENDING, CONFIRMED, FAILED, COMPENSATED}
}

