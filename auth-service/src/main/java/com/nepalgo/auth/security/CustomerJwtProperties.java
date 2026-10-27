package com.nepalgo.auth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepalgo.jwt.customer")
@Getter
@Setter
public class CustomerJwtProperties {
    private String privateKeyPath;
    private String publicKeyPath;
    private int accessTokenTtlMinutes;
    private int refreshTokenTtlDays;
}