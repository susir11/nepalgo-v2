package com.nepalgo.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.nepalgo.auth.security.CustomerJwtProperties;
import com.nepalgo.auth.security.StaffJwtProperties;
import java.util.TimeZone;

@EnableConfigurationProperties({CustomerJwtProperties.class, StaffJwtProperties.class})
@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		// Windows maps "Nepal Standard Time" to the misspelled IANA zone
		// "Asia/Katmandu" (missing the 'h') in some JDK builds, which
		// Postgres rejects outright at connection time. Forcing UTC here,
		// before Spring even starts building beans, sidesteps the whole
		// mapping bug — and UTC is the right standard for a service like
		// this anyway, same reasoning as storing timestamps in UTC and
		// converting only at the display layer.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(AuthServiceApplication.class, args);
	}
}