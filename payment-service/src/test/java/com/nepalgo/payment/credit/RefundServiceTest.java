package com.nepalgo.payment.credit;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.audit.AuditService;
import com.nepalgo.payment.bill.Bill;
import com.nepalgo.payment.bill.BillLineItem;
import com.nepalgo.payment.bill.BillService;
import com.nepalgo.payment.client.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// The cancellation money rules (spec 8.7) with the anti-laundering rule:
// cash refunds only ever return CASH that was paid; the credit-paid part
// comes back as a new credit. Bill used throughout:
//   NPR 10,000 + 10% service = NPR 11,000 = $110.00 at 100 NPR/USD
//   $10 travel credit applied -> customer paid $100.00 cash
class RefundServiceTest {

    RefundRequestRepository refunds = mock(RefundRequestRepository.class);
    StoreCreditService credits = mock(StoreCreditService.class);
    BillService bills = mock(BillService.class);
    RefundService service = new RefundService(refunds, credits, bills, mock(AuditService.class), mock(NotificationPublisher.class));

    Bill bill;

    @BeforeEach
    void setUp() {
        bill = new Bill();
        bill.setId(7L);
        bill.setCustomerId(1L);
        bill.setStatus(Bill.Status.LOCKED);
        bill.setInvoiceNumber("NG-2026-000099");
        bill.setFxRate(new BigDecimal("100"));
        bill.setServiceChargePercent(new BigDecimal("10"));
        bill.setVatPercent(new BigDecimal("13"));
        bill.setVatRegistered(false);
        bill.setAppliedCreditAmount(new BigDecimal("10.00"));
        BillLineItem l = new BillLineItem();
        l.setQuantity(BigDecimal.ONE);
        l.setUnitPriceNpr(new BigDecimal("10000"));
        l.setLineTotalNpr(new BigDecimal("10000.00"));
        l.setCategory(BillLineItem.Category.ACCOMMODATION);
        l.setDescription("Hotel");
        bill.replaceLineItems(List.of(l));
        when(bills.currentForBooking(42L)).thenReturn(bill);
        when(refunds.findByBookingId(42L)).thenReturn(Optional.empty());
        when(refunds.save(any())).thenAnswer(inv -> { CreditEntities.RefundRequest r = inv.getArgument(0); r.setId(5L); return r; });
        when(credits.issueForCancellation(anyLong(), anyLong(), any(), anyString())).thenAnswer(inv -> {
            StoreCredit c = new StoreCredit();
            c.setCode("NG-CR-TEST01");
            c.setAmountUsd(inv.getArgument(2));
            return c;
        });
    }

    @Test
    void sixtyPlusDays_cashOnlyForCashPaid_creditPartBackAsCredit() {
        var out = service.cancel(42L, "60_PLUS_DAYS", new BigDecimal("90"), false);
        assertThat(out.cashRefundUsd()).isEqualByComparingTo("90.00");   // 90% of the $100 cash
        assertThat(out.creditUsd()).isEqualByComparingTo("9.00");        // 90% of the $10 credit -> credit, NOT cash
        assertThat(out.creditCode()).isEqualTo("NG-CR-TEST01");
    }

    @Test
    void thirtyToFiftyNine_noCash_wholeValueAsCredit() {
        var out = service.cancel(42L, "30_59_DAYS", new BigDecimal("70"), false);
        assertThat(out.cashRefundUsd()).isEqualByComparingTo("0");
        assertThat(out.creditUsd()).isEqualByComparingTo("77.00");       // 70% of $110 total
        verify(refunds, never()).save(any());
    }

    @Test
    void lastFourteenDays_nothing() {
        var out = service.cancel(42L, "0_14_DAYS", BigDecimal.ZERO, false);
        assertThat(out.cashRefundUsd()).isEqualByComparingTo("0");
        assertThat(out.creditUsd()).isEqualByComparingTo("0");
        verifyNoInteractions(credits);
    }

    @Test
    void forceMajeure_fullCashNoFee_evenWhenTierWouldSayOtherwise() {
        var out = service.cancel(42L, "0_14_DAYS", BigDecimal.ZERO, true);
        assertThat(out.tier()).isEqualTo("FORCE_MAJEURE");
        assertThat(out.cashRefundUsd()).isEqualByComparingTo("100.00");
        assertThat(out.creditUsd()).isEqualByComparingTo("10.00");       // the credit portion still returns as credit
    }

    @Test
    void unpaidBillCannotBeRefunded() {
        bill.setStatus(Bill.Status.DRAFT);
        assertThatThrownBy(() -> service.cancel(42L, "60_PLUS_DAYS", new BigDecimal("90"), false))
                .isInstanceOf(PaymentExceptions.InvalidBillState.class);
    }

    @Test
    void retryIsIdempotent_existingRefundReused() {
        CreditEntities.RefundRequest existing = new CreditEntities.RefundRequest();
        existing.setId(5L);
        existing.setAmountUsd(new BigDecimal("90.00"));
        when(refunds.findByBookingId(42L)).thenReturn(Optional.of(existing));
        var out = service.cancel(42L, "60_PLUS_DAYS", new BigDecimal("90"), false);
        assertThat(out.refundRequestId()).isEqualTo(5L);
        verify(refunds, never()).save(any());                          // no second refund, ever
    }
}
