package com.eventledger.gateway.repository;

import com.eventledger.gateway.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventId(String eventId);

    @Query("SELECT e FROM Event e WHERE e.accountId = :accountId ORDER BY e.eventTimestamp ASC")
    List<Event> findByAccountIdOrderByTimestamp(@Param("accountId") String accountId);

    @Query("SELECT e FROM Event e WHERE e.accountId = :accountId AND e.status = 'PROCESSED' ORDER BY e.eventTimestamp ASC")
    List<Event> findProcessedEventsByAccountId(@Param("accountId") String accountId);
}

