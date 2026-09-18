package com.meridian.backend.repository;

import com.meridian.backend.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Alert> findByTickerIdAndTriggeredFalse(Long tickerId);
}
