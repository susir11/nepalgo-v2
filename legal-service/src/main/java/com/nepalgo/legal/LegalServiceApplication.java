package com.nepalgo.legal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class LegalServiceApplication {

    public static void main(String[] args) {
        // Same fix as every service: Windows reports "Asia/Katmandu", which the
        // Postgres JDBC session rejects. Run the JVM in UTC; convert at the edges.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LegalServiceApplication.class, args);
    }
}
