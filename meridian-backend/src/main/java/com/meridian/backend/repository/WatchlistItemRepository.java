package com.meridian.backend.repository;

import com.meridian.backend.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WatchlistItem> findByUserIdAndTickerId(Long userId, Long tickerId);

    boolean existsByUserIdAndTickerId(Long userId, Long tickerId);
}
