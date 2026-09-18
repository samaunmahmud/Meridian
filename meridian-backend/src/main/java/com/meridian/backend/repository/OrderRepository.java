package com.meridian.backend.repository;

import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<Order> findByPortfolioIdAndStatusOrderByCreatedAtDesc(Long portfolioId, OrderStatus status);

    List<Order> findByTickerIdAndStatus(Long tickerId, OrderStatus status);

    Optional<Order> findByIdAndPortfolioId(Long id, Long portfolioId);
}
