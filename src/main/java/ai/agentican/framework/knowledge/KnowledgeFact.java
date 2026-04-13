package ai.agentican.framework.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeFact(
        String id,
        String name,
        String content,
        List<String> tags,
        Instant created,
        Instant updated) {

    public KnowledgeFact {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Fact id is required");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Fact name is required");

        if (content == null) content = "";
        if (tags == null) tags = List.of();
        if (created == null) created = Instant.now();
        if (updated == null) updated = created;

        tags = List.copyOf(tags);
    }

    public static KnowledgeFact of(String name, String content, List<String> tags) {

        var now = Instant.now();

        return new KnowledgeFact(UUID.randomUUID().toString(), name, content, tags, now, now);
    }
}
