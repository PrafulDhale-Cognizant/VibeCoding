package com.simplifiedbilling.shared.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlywayRecoveryConfigurationTest {

    @Mock private Flyway flyway;
    @Mock private MigrationInfoService migrationInfoService;

    @BeforeEach
    void setUp() {
        when(flyway.info()).thenReturn(migrationInfoService);
    }

    @Test
    void repairsOneInterruptedV9MigrationBeforeMigrating() {
        MigrationInfo migration = migration(MigrationState.FAILED, "9");
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{migration});

        strategy().migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    void doesNotRepairFailedMigrationFromAnotherVersion() {
        MigrationInfo migration = migration(MigrationState.FAILED, "8");
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{migration});

        strategy().migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void doesNotRepairFailedMigrationWithoutVersion() {
        MigrationInfo migration = migration(MigrationState.FAILED, null);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{migration});

        strategy().migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void migratesNormallyWhenThereIsNoFailedMigration() {
        MigrationInfo migration = migration(MigrationState.SUCCESS, "9");
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{migration});

        strategy().migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    private org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy strategy() {
        return new FlywayRecoveryConfiguration().recoverInterruptedSalesReturnCostMigration();
    }

    private MigrationInfo migration(MigrationState state, String version) {
        MigrationInfo migration = mock(MigrationInfo.class);
        when(migration.getState()).thenReturn(state);
        if (state.isFailed()) {
            when(migration.getVersion()).thenReturn(
                    version == null ? null : MigrationVersion.fromVersion(version));
        }
        return migration;
    }
}
