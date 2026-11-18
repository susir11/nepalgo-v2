package com.nepalgo.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Model.ChatMessage, Long> {
    List<Model.ChatMessage> findByThreadIdOrderByIdAsc(Long threadId);
}
