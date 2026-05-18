package ai.agentican.framework.examples.temporal;

import java.util.Map;

public record MarketBriefParams(String topic, int vendorCount) {

    public MarketBriefParams {

        if (topic == null || topic.isBlank())
            throw new IllegalArgumentException("topic is required");

        if (vendorCount < 1) vendorCount = 5;
    }

    public Map<String, String> asMap() {

        return Map.of(
                "topic",        topic,
                "vendor_count", String.valueOf(vendorCount));
    }
}
