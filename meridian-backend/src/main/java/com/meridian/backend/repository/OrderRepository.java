package com.meridian.backend.repository;

import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<Order> findByPortfolioIdAndStatusOrderByCreatedAtDesc(Long portfolioId, OrderStatus status);

    List<Order> findByTickerIdAndStatus(Long tickerId, OrderStatus status);

    Optional<Order> findByIdAndPortfolioId(Long id, Long portfolioId);

    // IDs only: the scheduler processes each pending order in its own
    // transaction and re-reads it fresh after taking the portfolio lock.
    @Query("select o.id from Order o where o.ticker.id = :tickerId and o.status = :status")
    List<Long> findIdsByTickerIdAndStatus(@Param("tickerId") Long tickerId, @Param("status") OrderStatus status);

    // Tickers that someone is waiting on: the price poller asks for these first at the open.
    @Query("select distinct o.ticker.id from Order o where o.status = :status")
    List<Long> findDistinctTickerIdsByStatus(@Param("status") OrderStatus status);

    @Query("select o.portfolio.id from Order o where o.id = :id")
    Optional<Long> findPortfolioIdById(@Param("id") Long id);
}
