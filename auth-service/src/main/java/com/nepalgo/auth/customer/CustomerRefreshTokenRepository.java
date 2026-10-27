package com.nepalgo.auth.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {
    Optional<CustomerRefreshToken> findByTokenHash(String tokenHash);
    List<CustomerRefreshToken> findByTokenFamily(UUID tokenFamily);
}