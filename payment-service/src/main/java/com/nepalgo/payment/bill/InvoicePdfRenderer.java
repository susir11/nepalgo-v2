package com.nepalgo.payment.bill;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Renders a bill as a PDF invoice (spec section 21).
//
// This is the ONE document where NPR and USD appear together: NPR is the tax
// record, USD is what the customer paid — both clearly labelled. Field set
// follows VAT Rules 2053, Rule 17 (title, seller+PAN/VAT, buyer, sequential
// number, date, itemised qty/rate, subtotal, VAT amount+rate, total in figures
// and words, signature space).
//
// NOTE FOR GO-LIVE: confirm the exact current invoice requirements with a
// Nepali accountant; invoice rules have been amended before.
//
// OpenPDF 3.x uses the org.openpdf.* packages (older tutorials show com.lowagie.*).
public final class InvoicePdfRenderer {

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font H = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font N = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.DARK_GRAY);
    private static final Font STAMP = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(170, 30, 30));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private InvoicePdfRenderer() {}

    // correctsInvoiceNumber: set when this bill is a correction of an earlier one.
    // supersededBy: set when this bill was later replaced by a correction.
    public static byte[] render(Bill bill, String correctsInvoiceNumber, String supersededBy) {
        if (!bill.isIssued()) {
            throw new IllegalStateException("Only issued bills have an invoice");
        }
        BillCalculator.Totals t = BillService.totals(bill);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // ---- header
        Paragraph title = new Paragraph(BillService.documentTitle(bill), TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        Paragraph status = new Paragraph(statusLine(bill, correctsInvoiceNumber, supersededBy), STAMP);
        status.setAlignment(Element.ALIGN_CENTER);
        status.setSpacingAfter(10);
        doc.add(status);

        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.addCell(box("SELLER\n" + bill.getSellerLegalName() + "\n" + bill.getSellerAddress()
                + "\nPAN/VAT No.: " + bill.getSellerPanVatNumber()));
        parties.addCell(box("INVOICE No.: " + bill.getInvoiceNumber()
                + "\nIssue date: " + DATE.format(bill.getIssueDate())
                + "\n\nBUYER\n" + (bill.getBuyerName() == null ? "(lead booker)" : bill.getBuyerName())
                + (bill.getBuyerPan() == null ? "" : "\nBuyer PAN: " + bill.getBuyerPan())
                + "\nBooking ref: " + bill.getBookingId()));
        doc.add(parties);
        doc.add(new Paragraph(" ", N));

        // ---- lines
        PdfPTable lines = new PdfPTable(new float[]{0.5f, 5f, 1f, 1.6f, 1.8f, 1.5f});
        lines.setWidthPercentage(100);
        for (String h : new String[]{"#", "Description", "Qty", "Rate (NPR)", "Amount (NPR)", "Amount (USD)"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, H));
            c.setBackgroundColor(new Color(235, 235, 235));
            lines.addCell(c);
        }
        for (BillLineItem l : bill.getLineItems()) {
            lines.addCell(cell(String.valueOf(l.getPosition()), Element.ALIGN_CENTER));
            lines.addCell(cell((l.getItineraryDay() == null ? "" : "Day " + l.getItineraryDay() + " - ")
                    + l.getDescription() + "  [" + l.getCategory() + "]", Element.ALIGN_LEFT));
            lines.addCell(cell(l.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            lines.addCell(cell(money(l.getUnitPriceNpr()), Element.ALIGN_RIGHT));
            lines.addCell(cell(money(l.getLineTotalNpr()), Element.ALIGN_RIGHT));
            lines.addCell(cell(money(BillCalculator.toUsd(l.getLineTotalNpr(), bill.getFxRate())), Element.ALIGN_RIGHT));
        }
        doc.add(lines);

        // ---- totals
        PdfPTable totals = new PdfPTable(new float[]{6f, 2f});
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(8);
        row(totals, "Subtotal (NPR)", money(t.subtotalNpr()), false);
        row(totals, "Service charge " + pct(bill.getServiceChargePercent()) + " (NPR)", money(t.serviceChargeNpr()), false);
        if (bill.isVatRegistered()) {
            row(totals, "Taxable amount (NPR)", money(t.taxableAmountNpr()), false);
            row(totals, "VAT " + pct(bill.getVatPercent()) + " (NPR)", money(t.vatNpr()), false);
        }
        row(totals, "GRAND TOTAL (NPR)", money(t.grandTotalNpr()), true);
        row(totals, "Exchange rate (" + bill.getCurrencyPair() + ")", bill.getFxRate().stripTrailingZeros().toPlainString(), false);
        row(totals, "Grand total (USD)", money(t.grandTotalUsd()), false);
        if (t.appliedCreditUsd().signum() > 0) {
            row(totals, "Travel credit applied (USD)", "- " + money(t.appliedCreditUsd()), false);
        }
        row(totals, "AMOUNT PAYABLE (USD)", money(t.payableUsd()), true);
        doc.add(totals);

        Paragraph words = new Paragraph("In words: " + AmountInWords.format(t.grandTotalNpr()), H);
        words.setSpacingBefore(8);
        doc.add(words);

        doc.add(new Paragraph("NPR is the tax-record currency. The customer was billed in USD at the exchange rate "
                + "shown above, fixed when this bill was created.", SMALL));

        // ---- signature / seal space
        Paragraph sig = new Paragraph("\n\n\n______________________________\nAuthorised signature & seal", N);
        sig.setAlignment(Element.ALIGN_RIGHT);
        doc.add(sig);

        doc.close();
        return out.toByteArray();
    }

    private static String statusLine(Bill bill, String corrects, String supersededBy) {
        StringBuilder sb = new StringBuilder(bill.getStatus() == Bill.Status.LOCKED ? "PAID" : "PAYMENT DUE");
        if (corrects != null) sb.append("  |  CORRECTION of invoice ").append(corrects);
        if (supersededBy != null) sb.append("  |  SUPERSEDED by invoice ").append(supersededBy);
        return sb.toString();
    }

    private static PdfPCell box(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, N));
        c.setPadding(6);
        return c;
    }

    private static PdfPCell cell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, N));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private static void row(PdfPTable table, String label, String value, boolean bold) {
        Font f = bold ? H : N;
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        PdfPCell v = new PdfPCell(new Phrase(value, f));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setBorder(Rectangle.NO_BORDER);
        v.setBorder(Rectangle.NO_BORDER);
        table.addCell(l);
        table.addCell(v);
    }

    // 1,25,000.00 style is common in Nepal, but plain thousands grouping is
    // unambiguous for foreign customers too: 125,000.00
    private static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static String pct(BigDecimal p) {
        return "@ " + p.stripTrailingZeros().toPlainString() + "%";
    }
}
