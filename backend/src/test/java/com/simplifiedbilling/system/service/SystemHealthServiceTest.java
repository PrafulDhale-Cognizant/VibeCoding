package com.simplifiedbilling.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemHealthServiceTest {

    @Test
    void reportsDevelopmentAndUnknownDatabaseFallbacks() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> builds = mock(ObjectProvider.class);
        when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(0);
        when(builds.getIfAvailable()).thenReturn(null);

        var response = new SystemHealthService(jdbc, builds).getHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("UNKNOWN");
        assertThat(response.version()).isEqualTo("development");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void reportsBuildVersionAndHealthyDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> builds = mock(ObjectProvider.class);
        Properties properties = new Properties();
        properties.setProperty("version", "1.2.3");
        when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(builds.getIfAvailable()).thenReturn(new BuildProperties(properties));

        var response = new SystemHealthService(jdbc, builds).getHealth();

        assertThat(response.database()).isEqualTo("UP");
        assertThat(response.version()).isEqualTo("1.2.3");
        assertThat(response.javaVersion()).isNotBlank();
    }
}
