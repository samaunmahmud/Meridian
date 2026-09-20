package com.meridian.backend.repository;

import com.meridian.backend.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TickerRepository extends JpaRepository<Ticker, Long> {

    @org.springframework.data.jpa.repository.Query("select t.id from Ticker t")
    java.util.List<Long> findAllIds();

    Optional<Ticker> findBySymbol(String symbol);
}
