package com.simplifiedbilling.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplifiedbilling.shared.config.CorrelationIdFilter;
import com.simplifiedbilling.shared.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                "AUTHENTICATION_REQUIRED",
                "A valid access token is required.",
                List.of(),
                Instant.now(),
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
