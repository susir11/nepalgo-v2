package com.nepalgo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling   // the delivery worker polls the outbox
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // Windows "Asia/Katmandu" vs Postgres (Phase 1 pitfall)
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
