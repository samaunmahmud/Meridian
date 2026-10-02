package com.meridian.backend.repository;

import com.meridian.backend.model.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    Optional<Dividend> findByTickerIdAndExDate(Long tickerId, LocalDate exDate);

    /** Newest ex-date first. */
    List<Dividend> findByTickerIdOrderByExDateDesc(Long tickerId);

    /** Dividends whose payment date has come and that have not been paid out yet. */
    List<Dividend> findByPaidOutAtIsNullAndPayDateLessThanEqualOrderByPayDateAsc(LocalDate today);
}
