package com.simplifiedbilling.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.pos")
public record PosProperties(boolean pricesIncludeGst, boolean roundPayable) {
}
