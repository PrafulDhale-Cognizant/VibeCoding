package com.simplifiedbilling.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplifiedbilling.shared.config.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditWriter {

    private static final String INSERT_SQL = """
            INSERT INTO audit_events
                (id, actor_user_id, event_type, entity_type, entity_id, correlation_id, details, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(
            String actorUserId,
            String eventType,
            String entityType,
            String entityId,
            Map<String, ?> details) {

        jdbcTemplate.update(
                INSERT_SQL,
                UUID.randomUUID().toString(),
                actorUserId,
                eventType,
                entityType,
                entityId,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                toJson(details),
                Timestamp.from(Instant.now(clock)));
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit details.", exception);
        }
    }
}
