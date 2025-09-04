package com.example.los_service.repo;

import com.example.los_service.model.Participation.Status;

import com.example.los_service.model.Participation;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
@Repository
public interface ParticipationRepo extends JpaRepository<Participation, Long> {
    long countByStatus(Status status);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
