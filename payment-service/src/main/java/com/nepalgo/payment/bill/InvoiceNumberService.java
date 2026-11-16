package com.nepalgo.payment.bill;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

// Hands out gap-resistant, never-duplicated invoice numbers: NG-2026-000001.
//
// Why not a Postgres SEQUENCE? Sequences don't reset per year and are not
// transactional (a rolled-back issue still burns a number). Why not
// "SELECT max(...) + 1"? Two admins issuing at the same instant would both read
// the same max and collide. The UPSERT below is ONE atomic statement that
// row-locks the year's counter, so concurrent callers queue up and each gets
// the next value — like a bank's cheque-number register.
@Service
public class InvoiceNumberService {

    // Business dates are Nepal-local even though the JVM runs in UTC: a bill
    // issued at 02:00 on 1 Jan in Kathmandu belongs to the new year.
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kathmandu");

    @PersistenceContext
    private EntityManager em;

    // MANDATORY: must run inside the caller's transaction, so if issuing the
    // bill fails afterwards, the counter increment rolls back with it (no gap).
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(LocalDate issueDate) {
        int year = issueDate.getYear();
        Number value = (Number) em.createNativeQuery("""
                        INSERT INTO invoice_sequences (seq_year, last_value) VALUES (:year, 1)
                        ON CONFLICT (seq_year) DO UPDATE SET last_value = invoice_sequences.last_value + 1
                        RETURNING last_value
                        """)
                .setParameter("year", year)
                .getSingleResult();
        return String.format("NG-%d-%06d", year, value.longValue());
    }

    public static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
