package com.example.ledger_service.repo;

import com.example.ledger_service.model.InboxMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public interface InboxRepo extends JpaRepository<InboxMessage, UUID> {}
