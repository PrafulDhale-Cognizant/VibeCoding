package com.simplifiedbilling.shared.config;

import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class FlywayRecoveryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayRecoveryConfiguration.class);
    private static final String RECOVERABLE_VERSION = "9";

    @Bean
    FlywayMigrationStrategy recoverInterruptedSalesReturnCostMigration() {
        return flyway -> {
            List<MigrationInfo> failed = Arrays.stream(flyway.info().all())
                    .filter(info -> info.getState().isFailed())
                    .toList();
            if (failed.size() == 1 && isRecoverableV9(failed.getFirst())) {
                LOGGER.warn("Repairing the interrupted, rerunnable Flyway V9 migration before startup.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    private boolean isRecoverableV9(MigrationInfo migration) {
        return migration.getVersion() != null
                && RECOVERABLE_VERSION.equals(migration.getVersion().getVersion());
    }
}
