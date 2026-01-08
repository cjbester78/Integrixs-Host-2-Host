package com.integrixs.shared.model.value;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object representing schedule configuration settings.
 * Part of Phase 5 model layer refactoring following OOP principles.
 */
public final class ScheduleSettings {
    
    private final boolean enabled;
    private final String cronExpression;
    private final LocalDateTime nextScheduledRun;
    
    private ScheduleSettings(boolean enabled, String cronExpression, LocalDateTime nextScheduledRun) {
        this.enabled = enabled;
        this.cronExpression = enabled ? cronExpression : null;
        this.nextScheduledRun = enabled ? nextScheduledRun : null;
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public Optional<String> getCronExpression() { return Optional.ofNullable(cronExpression); }
    public Optional<LocalDateTime> getNextScheduledRun() { return Optional.ofNullable(nextScheduledRun); }
    
    /**
     * Check if schedule is overdue.
     */
    public boolean isOverdue() {
        return enabled && nextScheduledRun != null && nextScheduledRun.isBefore(LocalDateTime.now());
    }
    
    /**
     * Check if schedule is valid.
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // Disabled schedule is valid
        }
        return cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    /**
     * Get schedule status description.
     */
    public String getStatusDescription() {
        if (!enabled) {
            return "Disabled";
        }
        if (nextScheduledRun == null) {
            return "Scheduled (next run pending)";
        }
        if (isOverdue()) {
            return "Overdue";
        }
        return "Scheduled for " + nextScheduledRun;
    }
    
    /**
     * Create updated schedule with new next run time.
     */
    public ScheduleSettings withNextRun(LocalDateTime newNextRun) {
        if (!enabled) {
            return this; // Can't update disabled schedule
        }
        return new ScheduleSettings(enabled, cronExpression, newNextRun);
    }
    
    /**
     * Create enabled schedule.
     */
    public static ScheduleSettings enabled(String cronExpression, LocalDateTime nextRun) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression cannot be null or empty for enabled schedule");
        }
        return new ScheduleSettings(true, cronExpression.trim(), nextRun);
    }
    
    /**
     * Create enabled schedule with default next run (1 hour from now).
     */
    public static ScheduleSettings enabled(String cronExpression) {
        return enabled(cronExpression, LocalDateTime.now().plusHours(1));
    }
    
    /**
     * Create disabled schedule.
     */
    public static ScheduleSettings disabled() {
        return new ScheduleSettings(false, null, null);
    }
    
    /**
     * Create schedule from legacy values.
     */
    public static ScheduleSettings fromLegacy(Boolean scheduleEnabled, String scheduleCron, LocalDateTime nextScheduledRun) {
        if (scheduleEnabled != null && scheduleEnabled) {
            return enabled(scheduleCron != null ? scheduleCron : "0 0 2 * * ?", nextScheduledRun);
        }
        return disabled();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleSettings that = (ScheduleSettings) o;
        return enabled == that.enabled &&
               Objects.equals(cronExpression, that.cronExpression) &&
               Objects.equals(nextScheduledRun, that.nextScheduledRun);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(enabled, cronExpression, nextScheduledRun);
    }
    
    @Override
    public String toString() {
        if (!enabled) {
            return "ScheduleSettings{disabled}";
        }
        return String.format("ScheduleSettings{cron='%s', nextRun=%s}", cronExpression, nextScheduledRun);
    }
}