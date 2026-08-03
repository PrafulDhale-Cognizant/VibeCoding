package com.simplifiedbilling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BillingApplication {

    private static final Logger log = LoggerFactory.getLogger(BillingApplication.class);

    public static void main(String[] args) {
        log.info("Starting Simplified Billing backend");
        SpringApplication.run(BillingApplication.class, args);
        log.info("Simplified Billing backend started successfully");
    }
}
