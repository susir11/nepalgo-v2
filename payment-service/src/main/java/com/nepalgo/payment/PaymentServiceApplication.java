package com.nepalgo.payment;

import com.nepalgo.payment.config.FeatureFlags;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@EnableConfigurationProperties(FeatureFlags.class)
@SpringBootApplication
public class PaymentServiceApplication {

	public static void main(String[] args) {
		// Same Windows "Asia/Katmandu" mis-mapping fix as every other service.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}