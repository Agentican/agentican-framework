package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkerConfig(
        int maxTurns,
        Duration timeout,
        Duration taskTimeout,
        int maxStepRetries,
        int llmMaxRetries,
        Duration llmRetryBaseDelay) {

    public static final int DEFAULT_MAX_STEP_RETRIES = 3;
    public static final int DEFAULT_LLM_MAX_RETRIES = 3;
    public static final Duration DEFAULT_LLM_RETRY_BASE_DELAY = Duration.ofSeconds(1);

    public WorkerConfig {

        if (maxTurns == 0) maxTurns = 10;
        if (timeout == null) timeout = Duration.ofMinutes(30);
        if (maxStepRetries == 0) maxStepRetries = DEFAULT_MAX_STEP_RETRIES;
        if (llmMaxRetries == 0) llmMaxRetries = DEFAULT_LLM_MAX_RETRIES;
        if (llmRetryBaseDelay == null) llmRetryBaseDelay = DEFAULT_LLM_RETRY_BASE_DELAY;
    }

    public WorkerConfig(int maxTurns, Duration timeout) {

        this(maxTurns, timeout, null, 0, 0, null);
    }

    public WorkerConfig(int maxTurns, Duration timeout, Duration taskTimeout) {

        this(maxTurns, timeout, taskTimeout, 0, 0, null);
    }

    public WorkerConfig(int maxTurns, Duration timeout, Duration taskTimeout, int maxStepRetries) {

        this(maxTurns, timeout, taskTimeout, maxStepRetries, 0, null);
    }

    public static WorkerConfigBuilder builder() {

        return new WorkerConfigBuilder();
    }

    public static class WorkerConfigBuilder {

        private int maxTurns;
        private Duration timeout;
        private Duration taskTimeout;
        private int maxStepRetries;
        private int llmMaxRetries;
        private Duration llmRetryBaseDelay;

        public WorkerConfigBuilder maxTurns(int maxTurns) { this.maxTurns = maxTurns; return this; }
        public WorkerConfigBuilder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public WorkerConfigBuilder taskTimeout(Duration taskTimeout) { this.taskTimeout = taskTimeout; return this; }
        public WorkerConfigBuilder maxStepRetries(int maxStepRetries) { this.maxStepRetries = maxStepRetries; return this; }
        public WorkerConfigBuilder llmMaxRetries(int llmMaxRetries) { this.llmMaxRetries = llmMaxRetries; return this; }
        public WorkerConfigBuilder llmRetryBaseDelay(Duration llmRetryBaseDelay) { this.llmRetryBaseDelay = llmRetryBaseDelay; return this; }

        public WorkerConfig build() {

            return new WorkerConfig(maxTurns, timeout, taskTimeout, maxStepRetries, llmMaxRetries, llmRetryBaseDelay);
        }
    }
}
