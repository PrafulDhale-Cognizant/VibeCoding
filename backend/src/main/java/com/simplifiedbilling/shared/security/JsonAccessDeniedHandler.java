package com.simplifiedbilling.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplifiedbilling.shared.config.CorrelationIdFilter;
import com.simplifiedbilling.shared.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                "ACCESS_DENIED",
                "You do not have permission to perform this operation.",
                List.of(),
                Instant.now(),
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
