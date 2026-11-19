package com.nepalgo.catalog.destination;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "destination_images")
@Getter
@Setter
@NoArgsConstructor
public class DestinationImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "thumbnail_key", nullable = false, length = 500)
    private String thumbnailKey;   // object key in the images bucket, NOT a URL (see V3)

    @Column(name = "medium_key", nullable = false, length = 500)
    private String mediumKey;   // object key in the images bucket, NOT a URL (see V3)

    @Column(name = "full_key", nullable = false, length = 500)
    private String fullKey;   // object key in the images bucket, NOT a URL (see V3)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}