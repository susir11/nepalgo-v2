package com.nepalgo.auth.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByEmail(String email);
    long count(); // used by the bootstrap check below
}