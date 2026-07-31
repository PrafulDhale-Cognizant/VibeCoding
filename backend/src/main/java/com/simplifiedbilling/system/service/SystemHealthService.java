package com.simplifiedbilling.system.service;

import com.simplifiedbilling.system.dto.SystemHealthResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<BuildProperties> buildProperties;

    public SystemHealthService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<BuildProperties> buildProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.buildProperties = buildProperties;
    }

    @Transactional(readOnly = true)
    public SystemHealthResponse getHealth() {
        Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
        String databaseStatus = Integer.valueOf(1).equals(result) ? "UP" : "UNKNOWN";
        BuildProperties build = buildProperties.getIfAvailable();

        return new SystemHealthResponse(
                "UP",
                "billing-backend",
                build == null ? "development" : build.getVersion(),
                databaseStatus,
                Runtime.version().feature() + "",
                Instant.now());
    }
}
