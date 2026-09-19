package com.meridian.backend.repository;

import com.meridian.backend.model.RateLimitEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {

    long countByBucketAndOccurredAtAfter(String bucket, Instant cutoff);

    // Event times inside the window, oldest first (use a Pageable to pick one).
    @Query("select e.occurredAt from RateLimitEvent e where e.bucket = :bucket and e.occurredAt > :cutoff order by e.occurredAt")
    List<Instant> occurrencesAfter(@Param("bucket") String bucket, @Param("cutoff") Instant cutoff, Pageable page);

    @Modifying
    @Query("delete from RateLimitEvent e where e.bucket = :bucket")
    int clearBucket(@Param("bucket") String bucket);

    @Modifying
    @Query("delete from RateLimitEvent e where e.bucket = :bucket and e.occurredAt <= :cutoff")
    int deleteExpiredInBucket(@Param("bucket") String bucket, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from RateLimitEvent e where e.occurredAt <= :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
