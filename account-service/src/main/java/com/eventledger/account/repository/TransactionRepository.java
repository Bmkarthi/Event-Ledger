package com.eventledger.account.repository;

import com.eventledger.account.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId ORDER BY t.createdAt ASC")
    List<Transaction> findByAccountIdOrderByCreatedAt(@Param("accountId") String accountId);
}

