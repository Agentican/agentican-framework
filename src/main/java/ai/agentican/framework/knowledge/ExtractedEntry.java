package ai.agentican.framework.knowledge;

import java.util.List;

public record ExtractedEntry(
        Action action,
        String existingEntryId,
        String name,
        String description,
        List<KnowledgeFact> facts) {

    public ExtractedEntry {

        if (action == null) throw new IllegalArgumentException("action is required");
        if (action == Action.UPDATE && (existingEntryId == null || existingEntryId.isBlank()))
            throw new IllegalArgumentException("existingEntryId is required for UPDATE");
        if (facts == null) facts = List.of();
    }

    public enum Action {

        CREATE,
        UPDATE;

        public static Action parse(String s) {

            if (s == null) return CREATE;
            return "update".equalsIgnoreCase(s.trim()) ? UPDATE : CREATE;
        }
    }
}
