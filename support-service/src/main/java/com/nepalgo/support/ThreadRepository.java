package com.nepalgo.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThreadRepository extends JpaRepository<Model.ChatThread, Long> {
    List<Model.ChatThread> findByCustomerIdOrderByLastMessageAtDesc(Long customerId);
    Page<Model.ChatThread> findByStatusOrderByLastMessageAtDesc(Model.ChatThread.Status status, Pageable p);
}
