package ai.agentican.framework.llm;

import java.util.List;
import java.util.Map;

public record Message(Role role, List<Block> blocks) {

    public Message {

        if (role == null) throw new IllegalArgumentException("Message role is required");

        if (blocks == null) blocks = List.of();

        blocks = List.copyOf(blocks);
    }

    public enum Role { USER, ASSISTANT }

    public sealed interface Block permits TextBlock, ToolUseBlock, ToolResultBlock {}

    public record TextBlock(String text) implements Block {

        public TextBlock {
            if (text == null) text = "";
        }
    }

    public record ToolUseBlock(String id, String toolName, Map<String, Object> args) implements Block {

        public ToolUseBlock {

            if (id == null || id.isBlank()) throw new IllegalArgumentException("tool_use id is required");
            if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("name is required");

            if (args == null) args = Map.of();

            args = Map.copyOf(args);
        }
    }

    public record ToolResultBlock(String toolUseId, String content, boolean isError) implements Block {

        public ToolResultBlock {

            if (toolUseId == null || toolUseId.isBlank())
                throw new IllegalArgumentException("toolUseId is required");

            if (content == null) content = "";
        }

        public ToolResultBlock(String toolUseId, String content) {
            this(toolUseId, content, false);
        }
    }

    public static Message user(Block... blocks) {

        return new Message(Role.USER, List.of(blocks));
    }

    public static Message assistant(Block... blocks) {

        return new Message(Role.ASSISTANT, List.of(blocks));
    }

    public static Message user(List<Block> blocks) {

        return new Message(Role.USER, blocks);
    }

    public static Message assistant(List<Block> blocks) {

        return new Message(Role.ASSISTANT, blocks);
    }
}
