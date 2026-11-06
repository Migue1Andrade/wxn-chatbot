package com.wxn.chatbot.sla.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sla_policy")
public class SlaPolicy {

    @Id
    private Long id = 1L;

    private boolean enabled;
    private int firstResponseMinutes = 30;
    private int nextResponseMinutes = 30;
    private int resolutionMinutes = 1440;
    private int warningThresholdPercent = 80;
    private String timezone = "UTC";

    @Version
    private Long version;

    protected SlaPolicy() {}

    public boolean isEnabled() {
        return enabled;
    }

    public int getFirstResponseMinutes() {
        return firstResponseMinutes;
    }

    public int getNextResponseMinutes() {
        return nextResponseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public int getWarningThresholdPercent() {
        return warningThresholdPercent;
    }

    public String getTimezone() {
        return timezone;
    }

    public Long getVersion() {
        return version;
    }

    public void update(boolean enabled, int first, int next, int resolution, int warning, String timezone) {
        this.enabled = enabled;
        this.firstResponseMinutes = first;
        this.nextResponseMinutes = next;
        this.resolutionMinutes = resolution;
        this.warningThresholdPercent = warning;
        this.timezone = timezone;
    }
}
