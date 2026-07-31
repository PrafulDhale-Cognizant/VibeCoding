package com.simplifiedbilling.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplifiedbilling.auth.repository.RefreshTokenRepository;
import com.simplifiedbilling.auth.repository.UserAccountRepository;
import com.simplifiedbilling.store.repository.ShopProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StoreAuthenticationFlowTest {

    private static final String SETUP_BODY = """
            {
              "store": {
                "shopName": "Local Grocery",
                "ownerName": "Asha Kumar",
                "addressLine1": "12 Market Road",
                "addressLine2": "",
                "city": "Pune",
                "stateName": "Maharashtra",
                "stateCode": "27",
                "postalCode": "411001",
                "phone": "9876543210",
                "email": "owner@example.test",
                "gstRegistered": false,
                "gstin": "",
                "currencyCode": "INR",
                "timezone": "Asia/Kolkata",
                "invoicePrefix": "INV",
                "financialYearStartMonth": 4,
                "receiptWidth": "MM_80"
              },
              "owner": {
                "username": "Admin",
                "displayName": "Asha Kumar",
                "password": "StrongLocal#123"
              },
              "dataResponsibilityAccepted": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ShopProfileRepository shopRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM audit_events");
    }

    @Test
    void bootstrapsOnceAndProtectsStoreApi() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        String setupJson = mockMvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.roles[0]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode setupResponse = objectMapper.readTree(setupJson);
        String accessToken = setupResponse.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/store"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/store")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Local Grocery"));

        mockMvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));
    }

    @Test
    void logsInAndRotatesRefreshTokenOnlyOnce() throws Exception {
        mockMvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SETUP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ADMIN","password":"StrongLocal#123"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(loginJson).path("refreshToken").asText();
        String refreshBody = objectMapper.writeValueAsString(
                java.util.Map.of("refreshToken", refreshToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
