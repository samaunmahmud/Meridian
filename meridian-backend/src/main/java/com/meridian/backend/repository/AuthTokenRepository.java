package com.meridian.backend.repository;

import com.meridian.backend.model.AuthToken;
import com.meridian.backend.model.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    // A new link replaces any earlier unused one, so only the newest email works.
    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
