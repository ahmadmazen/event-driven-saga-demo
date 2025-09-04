package com.example.ledger_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "wallet")
@Getter
@Setter
public class Wallet {
    @Id
    Long userId;
    BigDecimal balance;
    @Version
    Long version;
}

