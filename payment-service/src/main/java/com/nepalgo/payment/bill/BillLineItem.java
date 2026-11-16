package com.nepalgo.payment.bill;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// One priced line on the bill. Amounts are NPR on purpose: NPR is the
// tax-relevant bookkeeping currency; customers only ever see USD conversions.
@Entity
@Table(name = "bill_line_items")
@Getter
@Setter
@NoArgsConstructor
public class BillLineItem {

    public enum Category { TRANSPORT, ACCOMMODATION, FOOD, GUIDE, ACTIVITY, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "itinerary_day")
    private Integer itineraryDay;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price_npr", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPriceNpr;

    @Column(name = "line_total_npr", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotalNpr;
}
