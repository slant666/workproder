package com.example.workorder.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private boolean deliveryEnabled;
    private String from = "no-reply@work-order.local";
    private int maxAttempts = 5;
    private int batchSize = 20;
    private int verificationCodeTtlMinutes = 10;
    private int passwordResetTtlMinutes = 30;

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getVerificationCodeTtlMinutes() {
        return verificationCodeTtlMinutes;
    }

    public void setVerificationCodeTtlMinutes(int verificationCodeTtlMinutes) {
        this.verificationCodeTtlMinutes = verificationCodeTtlMinutes;
    }

    public int getPasswordResetTtlMinutes() {
        return passwordResetTtlMinutes;
    }

    public void setPasswordResetTtlMinutes(int passwordResetTtlMinutes) {
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }
}
