package com.nepalgo.catalog.itinerary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "itinerary_days")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_version_id", nullable = false)
    private ItineraryVersion itineraryVersion;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String narrative;

    // NULL hotelName = no curated hotel that day — section 6a's map card
    // simply doesn't render for that day.
    @Column(name = "hotel_name")
    private String hotelName;

    @Column(name = "hotel_lat", precision = 9, scale = 6)
    private BigDecimal hotelLat;

    @Column(name = "hotel_lng", precision = 9, scale = 6)
    private BigDecimal hotelLng;
}