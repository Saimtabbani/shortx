package com.shortx.repository;

import com.shortx.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // The refresh endpoint's core lookup, backed by uq_refresh_tokens_token_hash.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Powers a "your active sessions" / "log out other devices" UI.
    // Backed by idx_refresh_tokens_user_active.
    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    // Bulk revoke, used by "log out all devices" and by the security flow
    // that revokes every existing session on password change. A bulk
    // UPDATE avoids loading N entities into the persistence context just
    // to flip one boolean on each.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);

    // Housekeeping: permanently remove tokens that are both revoked AND
    // long expired, so this table doesn't grow unbounded with dead rows.
    // Invoked by a scheduled job, not on the request path.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.revoked = true OR r.expiresAt < CURRENT_TIMESTAMP")
    int deleteAllExpiredOrRevoked();
}
