package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemControllerTests {

    @Test
    void statusReturnsServiceStatus() {
        Map<String, Object> status = new SystemController().status();

        assertThat(status).containsEntry("status", "ok");
        assertThat(status).containsEntry("service", "work-order-system");
    }
}
