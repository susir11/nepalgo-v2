package com.nepalgo.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallbackRepository extends JpaRepository<Model.CallbackRequest, Long> {
    List<Model.CallbackRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Model.CallbackRequest> findByStatusOrderByCreatedAtAsc(Model.CallbackRequest.Status status);
}
