package com.psms.repository;

import com.psms.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    /** PK lookup — O(1) vì jti là Primary Key */
    boolean existsByJti(String jti);

    /** Cleanup scheduler: xóa JTI của token đã hết hạn (không cần giữ nữa) */
    @Modifying
    @Query("DELETE FROM RevokedAccessToken r WHERE r.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}

