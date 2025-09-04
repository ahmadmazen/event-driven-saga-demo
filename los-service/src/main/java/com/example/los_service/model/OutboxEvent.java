package com.example.los_service.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String eventType;

    String aggregateType;

    Long aggregateId;


   String payload;

    String status = "NEW";

    Integer attemptCount = 0;

    @PrePersist
    void prePersist() {
        if (status == null) status = "NEW";
        if (attemptCount == null) attemptCount = 0;
    }


}

