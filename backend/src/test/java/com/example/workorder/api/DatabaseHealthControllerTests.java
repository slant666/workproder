package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseHealthControllerTests {

    @Test
    void databaseReturnsValidationStatus() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        Map<String, Object> status = new DatabaseHealthController(jdbcTemplate).database();

        assertThat(status).containsEntry("status", "ok");
        assertThat(status).containsEntry("database", "mysql");
        assertThat(status).containsEntry("validation", 1);
    }
}