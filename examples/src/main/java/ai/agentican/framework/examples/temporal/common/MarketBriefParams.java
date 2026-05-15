package ai.agentican.framework.examples.temporal.common;

public record MarketBriefParams(String topic, int vendorCount) {

    public MarketBriefParams {

        if (topic == null || topic.isBlank())
            throw new IllegalArgumentException("topic is required");

        if (vendorCount < 1) vendorCount = 5;
    }
}
