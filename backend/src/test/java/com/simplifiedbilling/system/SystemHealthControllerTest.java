package com.simplifiedbilling.system;

import com.simplifiedbilling.shared.config.SecurityConfiguration;
import com.simplifiedbilling.system.controller.SystemHealthController;
import com.simplifiedbilling.system.dto.SystemHealthResponse;
import com.simplifiedbilling.system.service.SystemHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemHealthController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemHealthService healthService;

    @Test
    void exposesPublicHealthWithCorrelationId() throws Exception {
        when(healthService.getHealth()).thenReturn(new SystemHealthResponse(
                "UP",
                "billing-backend",
                "test",
                "UP",
                "21",
                Instant.parse("2026-07-31T00:00:00Z")));

        mockMvc.perform(get("/api/v1/system/health")
                        .header("X-Correlation-Id", "test-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-request-1"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void deniesUnconfiguredEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
