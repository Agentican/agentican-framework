# Agentican Quarkus Scheduler

> Cron-scheduled Agentican tasks, declared in `application.properties`.

Wraps [Quarkus Scheduler](https://quarkus.io/guides/scheduler) to fire `agentican.run(description)` on a cron schedule. No Java glue — declare the task in config and it shows up as a registered job at startup.

## Install

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-scheduler</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

Requires `agentican-quarkus-runtime` (for the `Agentican` bean) and `quarkus-scheduler` (transitive).

## Configure

```properties
# An agent to do the work
agentican.agents[0].name=analyst
agentican.agents[0].role=Reads the overnight queue and summarizes anomalies
agentican.agents[0].external-id=analyst

# A scheduled task
agentican.scheduled[0].name=nightly-digest
agentican.scheduled[0].cron=0 0 8 ? * MON-FRI
agentican.scheduled[0].description=Summarize yesterday's anomalies and post to #ops
agentican.scheduled[0].enabled=true
```

### Config schema (`agentican.scheduled[*]`)

| Property | Type | Default | Purpose |
|---|---|---|---|
| `name` | String | — | Quarkus Scheduler job name (must be unique). |
| `cron` | String | — | Quartz 6-field cron expression. |
| `description` | String | — | Natural-language task passed to `Agentican.run(...)`. |
| `enabled` | boolean | `true` | Skip registration when `false` without removing the block. |

### Cron quick reference

Quartz syntax — six fields: `second minute hour day-of-month month day-of-week`.

| Expression | Fires |
|---|---|
| `*/30 * * * * ?` | Every 30 seconds |
| `0 */5 * * * ?` | Every 5 minutes |
| `0 0 9 ? * MON-FRI` | Weekdays at 09:00 |
| `0 0 0 1 * ?` | Midnight on the 1st of every month |

## How it works

On `@PostConstruct`, [`AgenticanScheduler`](src/main/java/ai/agentican/quarkus/scheduler/AgenticanScheduler.java) reads [`ScheduledTaskConfig`](src/main/java/ai/agentican/quarkus/scheduler/ScheduledTaskConfig.java) and registers each enabled entry with the Quarkus `Scheduler`. On fire, the job calls `agentican.run(description)` and attaches a completion log to `handle.resultAsync()` — fire-and-forget from the scheduler's perspective. If you need retry, backpressure, or persistence of scheduled runs, compose it with the framework's `WorkerConfig`, `TaskListener`, or [`quarkus-store-jpa`](../quarkus-store-jpa/).

## Related

- [`quarkus-runtime`](../quarkus-runtime/) — the `Agentican` bean this module drives.
- [Top-level module index](../README.md#modules).
