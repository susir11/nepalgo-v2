package com.nepalgo.auth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepalgo.jwt.staff")
@Getter
@Setter
public class StaffJwtProperties {
    private String privateKeyPath;
    private String publicKeyPath;
    private int accessTokenTtlMinutes;
    private int refreshTokenIdleMinutes;
    private int refreshTokenAbsoluteHours;
}