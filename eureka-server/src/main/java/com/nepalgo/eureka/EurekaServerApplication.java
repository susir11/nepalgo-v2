package com.nepalgo.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

// EnableEurekaServer turns this plain Spring Boot app into a service registry —
// the equivalent of a BIC/SWIFT directory: every other service will register
// itself here on startup, and the gateway looks services up here by name
// instead of hardcoding IP:port pairs.
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaServerApplication.class, args);
	}
}