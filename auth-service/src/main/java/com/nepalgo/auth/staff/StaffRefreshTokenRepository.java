package com.nepalgo.auth.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRefreshTokenRepository extends JpaRepository<StaffRefreshToken, Long> {
    Optional<StaffRefreshToken> findByTokenHash(String tokenHash);
    List<StaffRefreshToken> findByTokenFamily(UUID tokenFamily);
}