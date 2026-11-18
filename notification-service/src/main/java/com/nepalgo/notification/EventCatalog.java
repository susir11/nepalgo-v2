package com.nepalgo.notification;

import com.nepalgo.notification.model.Delivery;
import com.nepalgo.notification.model.Notification;

import java.util.List;
import java.util.Map;

// Spec 19 notification matrix, as code. One place answers "who gets told what,
// over which channel" — adding an event type is a one-entry change here.
public final class EventCatalog {

    private EventCatalog() {}

    public record Template(Notification.Audience audience, List<Delivery.Channel> channels, boolean attachInvoice) {}

    public static final Map<String, Template> EVENTS = Map.of(
            // Admin approves & issues bill -> customer push
            "BILL_ISSUED", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH), false),
            // Booking created manually (8b) -> customer push "bill is ready"
            "MANUAL_BOOKING_BILL_READY", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH), false),
            // Customer uploads receipt -> admin in-app alert + email
            "RECEIPT_SUBMITTED", new Template(Notification.Audience.ADMIN, List.of(Delivery.Channel.EMAIL), false),
            // Payment verified -> customer push + email WITH invoice PDF
            "PAYMENT_VERIFIED", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH, Delivery.Channel.EMAIL), true),
            // Receipt rejected -> customer push
            "PAYMENT_REJECTED", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH), false),
            // Booking rejected (Step 1) -> customer push
            "BOOKING_REJECTED", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH), false),
            // Cash refund sent / travel credit issued -> customer push + email
            "REFUND_SENT", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH, Delivery.Channel.EMAIL), false),
            "CREDIT_ISSUED", new Template(Notification.Audience.CUSTOMER, List.of(Delivery.Channel.PUSH, Delivery.Channel.EMAIL), false));

    // Customer-facing copy. Amounts are always USD (spec 21 display rule).
    public static String[] render(String type, Map<String, Object> d) {
        String dest = str(d, "destinationName", "your trip");
        String voucher = str(d, "voucherCode", "");
        return switch (type) {
            case "BILL_ISSUED", "MANUAL_BOOKING_BILL_READY" -> new String[]{"Your bill is ready",
                    "Your itemised bill for " + dest + " is ready" + amount(d) + ". Review your itinerary and pay in the app."};
            case "RECEIPT_SUBMITTED" -> new String[]{"Receipt to verify: " + voucher,
                    "A customer uploaded a payment receipt for " + voucher + " (" + dest + "), code " + str(d, "receiptCode", "?")
                            + ". Check Payoneer, then verify or reject in the admin portal."};
            case "PAYMENT_VERIFIED" -> new String[]{"You're confirmed for " + dest + "!",
                    "Payment received. Your voucher is " + voucher + " — show its QR code to your hotel, driver or guide. "
                            + "Your invoice " + str(d, "invoiceNumber", "") + " is attached to the email we sent."};
            case "PAYMENT_REJECTED" -> new String[]{"Your payment needs attention",
                    "We couldn't verify your payment for " + dest + ": " + str(d, "reason", "please contact us")
                            + ". Your payment window has been reset — please upload a new receipt."};
            case "BOOKING_REJECTED" -> new String[]{"We couldn't arrange " + dest,
                    "Sorry — your trip request " + voucher + " couldn't be fulfilled: " + str(d, "reason", "no availability")
                            + ". Chat with us on WhatsApp for alternatives."};
            case "REFUND_SENT" -> new String[]{"Your refund is on its way",
                    "We've sent your refund of " + money(d, "amountUsd") + " via Payoneer (reference " + str(d, "payoutReference", "—")
                            + "). Banks can take a few days to show it."};
            case "CREDIT_ISSUED" -> new String[]{"You have a NepalGo Travel Credit",
                    money(d, "amountUsd") + " Travel Credit (" + str(d, "creditCode", "") + ") is in your wallet, valid until "
                            + str(d, "expiresOn", "—") + ". Apply it to your next trip request."};
            default -> throw new IllegalArgumentException("Unknown event type " + type);
        };
    }

    private static String amount(Map<String, Object> d) {
        return d.get("amountUsd") == null ? "" : " (" + money(d, "amountUsd") + ")";
    }

    private static String money(Map<String, Object> d, String k) {
        Object v = d.get(k);
        if (v == null) return "—";
        return String.format("$%,.2f", Double.parseDouble(v.toString()));
    }

    private static String str(Map<String, Object> d, String k, String def) {
        Object v = d.get(k);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }
}
