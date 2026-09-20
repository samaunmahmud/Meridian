package com.meridian.backend.repository;

import com.meridian.backend.model.Portfolio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    @Query("select p.id from Portfolio p")
    java.util.List<Long> findAllIds();

    Optional<Portfolio> findByUserId(Long userId);

    // Row-level lock (SELECT ... FOR UPDATE). Every operation that changes a
    // user's money or shares takes this lock FIRST, so two requests for the
    // same user run one after the other instead of both reading the same
    // balance, both passing the "enough funds?" check, and both spending it.
    // Always locking the portfolio row first (before holdings/orders/wallets)
    // also gives every code path the same lock order, which rules out deadlocks.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.user.id = :userId")
    Optional<Portfolio> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :id")
    Optional<Portfolio> findByIdForUpdate(@Param("id") Long id);
}
