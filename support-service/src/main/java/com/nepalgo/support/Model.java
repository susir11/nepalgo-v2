package com.nepalgo.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

public final class Model {

    private Model() {}

    @Entity
    @Table(name = "chat_threads")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChatThread {
        public enum Status { OPEN, CLOSED }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "customer_id", nullable = false)
        private Long customerId;
        @Column(name = "booking_id")
        private Long bookingId;
        @Column(nullable = false, length = 200)
        private String subject;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private Status status = Status.OPEN;
        @Column(name = "assigned_staff_id")
        private Long assignedStaffId;
        @Column(name = "last_message_at", nullable = false)
        private Instant lastMessageAt = Instant.now();
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Version
        private Long version;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    @Entity
    @Table(name = "chat_messages")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChatMessage {
        public enum Sender { CUSTOMER, STAFF }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "thread_id", nullable = false)
        private Long threadId;
        @Enumerated(EnumType.STRING)
        @Column(name = "sender_type", nullable = false, length = 10)
        private Sender senderType;
        @Column(name = "sender_id", nullable = false)
        private Long senderId;
        @Column(nullable = false, columnDefinition = "TEXT")
        private String body;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    @Entity
    @Table(name = "callback_requests")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CallbackRequest {
        public enum Status { PENDING, COMPLETED }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "customer_id", nullable = false)
        private Long customerId;
        @Column(name = "booking_id")
        private Long bookingId;
        @Column(name = "phone_number", nullable = false, length = 30)
        private String phoneNumber;
        @Column(name = "preferred_time", nullable = false, length = 100)
        private String preferredTime;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private Status status = Status.PENDING;
        @Column(name = "completed_by")
        private Long completedBy;
        @Column(name = "completed_at")
        private Instant completedAt;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
