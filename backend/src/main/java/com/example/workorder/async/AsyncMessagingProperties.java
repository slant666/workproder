package com.example.workorder.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.async")
public class AsyncMessagingProperties {

    private boolean rabbitEnabled = true;

    public boolean isRabbitEnabled() {
        return rabbitEnabled;
    }

    public void setRabbitEnabled(boolean rabbitEnabled) {
        this.rabbitEnabled = rabbitEnabled;
    }
}
