package ai.agentican.framework.orchestration.execution.resume;

public enum ReapReason {

    SERVER_RESTARTED,
    TASK_LOG_MISSING,
    PLAN_UNAVAILABLE,
    PLAN_CORRUPT,
    TASK_NOT_RUNNING,
    DANGLING_PARENT_TERMINAL,
    PARENT_REAPED,
    UNKNOWN;

    public static ReapReason parse(String raw) {

        if (raw == null || raw.isBlank()) return UNKNOWN;
        try { return ReapReason.valueOf(raw); }
        catch (IllegalArgumentException ignored) {}

        var upper = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        var colon = upper.indexOf(':');
        if (colon >= 0) upper = upper.substring(0, colon);
        try { return ReapReason.valueOf(upper); }
        catch (IllegalArgumentException ignored) { return UNKNOWN; }
    }
}
