package com.example.los_service.repo;


import com.example.los_service.model.OutboxEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface OutboxRepo extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(String status);

    long countByStatus(String aNew);
}
