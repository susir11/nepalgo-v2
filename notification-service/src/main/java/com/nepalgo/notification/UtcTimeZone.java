package com.nepalgo.notification;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

/**
 * Forces the JVM time zone to UTC as the very first step of Spring Boot startup,
 * however the app is launched (main(), IntelliJ, tests, a container).
 *
 * Why: Windows reports Nepal as "Asia/Katmandu" (old spelling). The PostgreSQL JDBC
 * driver sends the JVM zone as the session TimeZone and Postgres 17 rejects that
 * name, so the first DB connection fails and the service won't start. main() sets
 * UTC too, but anything that skips main() (tests, some launchers) didn't get it.
 * Registered in META-INF/spring.factories; runs before logging and the DataSource.
 * Like settling every ledger in one reference currency: store and compare in UTC,
 * convert only for display.
 */
public class UtcTimeZone implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
