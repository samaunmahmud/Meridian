package com.meridian.backend.repository;

import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<Order> findByPortfolioIdAndStatusOrderByCreatedAtDesc(Long portfolioId, OrderStatus status);

    List<Order> findByTickerIdAndStatus(Long tickerId, OrderStatus status);

    /** Orders on a ticker that reached `status` at or after `since` (filled orders: executed since then). */
    List<Order> findByTickerIdAndStatusAndExecutedAtGreaterThanEqual(Long tickerId, OrderStatus status, Instant since);

    /** A portfolio's first order of this type and status on a ticker, by execution time. */
    Optional<Order> findFirstByPortfolioIdAndTickerIdAndStatusAndTypeOrderByExecutedAtAsc(
            Long portfolioId, Long tickerId, OrderStatus status, OrderType type);

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
