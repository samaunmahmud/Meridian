package com.meridian.backend.repository;

import com.meridian.backend.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);
}
