package com.pk.core.business.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VnpayProperties.class)
public class VnpayConfiguration {
}
