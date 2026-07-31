package com.simplifiedbilling.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplifiedbilling.shared.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void writesNormalizedUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint(objectMapper).commence(
                request,
                response,
                new BadCredentialsException("bad"));

        ApiError error = objectMapper.readValue(response.getContentAsByteArray(), ApiError.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(error.code()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(error.path()).isEqualTo("/private");
    }

    @Test
    void writesNormalizedForbiddenResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("denied"));

        ApiError error = objectMapper.readValue(response.getContentAsByteArray(), ApiError.class);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(error.code()).isEqualTo("ACCESS_DENIED");
        assertThat(error.path()).isEqualTo("/admin");
    }
}
