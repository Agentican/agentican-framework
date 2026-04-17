# Scheduling

Requires `agentican-quarkus-scheduler`. Run Agentican tasks on a cron schedule.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-scheduler</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

## Configuration

```properties
# Daily research report at 9 AM
agentican.scheduled[0].name=daily-report
agentican.scheduled[0].cron=0 0 9 * * ?
agentican.scheduled[0].description=Generate a summary of recent research findings
agentican.scheduled[0].enabled=true

# Weekly cleanup, disabled for now
agentican.scheduled[1].name=weekly-cleanup
agentican.scheduled[1].cron=0 0 2 ? * SUN
agentican.scheduled[1].description=Archive completed tasks older than 7 days
agentican.scheduled[1].enabled=false
```

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.scheduled[*].name` | string | **required** | Task name (used in logs) |
| `agentican.scheduled[*].cron` | string | **required** | Quartz cron expression |
| `agentican.scheduled[*].description` | string | **required** | Sent to the planner |
| `agentican.scheduled[*].enabled` | boolean | `true` | Enable/disable |

## How it works

At startup, `AgenticanScheduler` reads the config and registers each enabled task with
the Quarkus `Scheduler` programmatic API. On each cron trigger:

1. The description is submitted to `Agentican.run(description)` (planner mode)
2. The task runs asynchronously on a virtual thread
3. On completion/failure, the result is logged

Scheduled tasks don't block each other — each trigger creates a new virtual thread.

## Cron expression format

Uses Quartz format with seconds: `seconds minutes hours day-of-month month day-of-week`

| Expression | Meaning |
|---|---|
| `0 0 9 * * ?` | Every day at 9:00 AM |
| `0 */30 * * * ?` | Every 30 minutes |
| `0 0 2 ? * SUN` | Every Sunday at 2:00 AM |
| `*/10 * * * * ?` | Every 10 seconds (testing) |
