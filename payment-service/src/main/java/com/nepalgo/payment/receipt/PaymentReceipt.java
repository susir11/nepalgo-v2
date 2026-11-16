package com.nepalgo.payment.receipt;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "payment_receipts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReceipt {

    // SUBMITTED = waiting for admin; ACCEPTED = the one the admin verified (bill locked)
    public enum Status { SUBMITTED, ACCEPTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "receipt_code", nullable = false, length = 100)
    private String receiptCode;

    @Column(name = "receipt_code_normalized", nullable = false, length = 100)
    private String receiptCodeNormalized;

    @Column(name = "object_key", nullable = false, unique = true, length = 300)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Fixed-width CHAR(64) in Postgres: a String maps to VARCHAR by default, so
    // tell Hibernate the JDBC type explicitly or `ddl-auto: validate` fails.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    // "pyn-12 34" and "PYN1234" are the same code to a fraudster, so compare them as equal.
    public static String normalize(String code) {
        return code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
