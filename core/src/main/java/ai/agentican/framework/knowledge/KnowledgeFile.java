package ai.agentican.framework.knowledge;

import java.time.Instant;

public record KnowledgeFile(
        String id,
        String name,
        String type,
        long size,
        byte[] contents,
        Instant created) {

    public KnowledgeFile {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("File id is required");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("File name is required");

        if (type == null) type = "";
        if (contents == null) contents = new byte[0];
        if (created == null) created = Instant.now();
    }

    public static KnowledgeFile of(String name, String type, byte[] contents) {

        return new KnowledgeFile(java.util.UUID.randomUUID().toString(), name, type,
                contents != null ? contents.length : 0, contents, Instant.now());
    }
}
