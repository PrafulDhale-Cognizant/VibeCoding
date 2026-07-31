package com.simplifiedbilling.auth.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UsernameNormalizer {

    public String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
