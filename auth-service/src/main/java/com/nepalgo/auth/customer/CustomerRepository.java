package com.nepalgo.auth.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByFirebaseUid(String firebaseUid);
    boolean existsByNepalgoId(String nepalgoId);
}