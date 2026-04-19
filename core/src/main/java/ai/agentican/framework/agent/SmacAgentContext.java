package ai.agentican.framework.agent;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.tools.scratchpad.ScratchpadToolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record SmacAgentContext(
        String systemPrompt,
        Map<String, Toolkit> toolkits,
        List<ToolDefinition> toolDefs,
        ScratchpadToolkit localScratchpad,
        ScratchpadToolkit sharedScratchpad,
        List<KnowledgeEntry> knowledgeIndex,
        Map<String, KnowledgeEntry> recalledKnowledge) {

    SmacAgentContext {

        if (recalledKnowledge == null) recalledKnowledge = new LinkedHashMap<>();
    }

    static Builder builder() {

        return new Builder();
    }

    static class Builder {

        private String systemPrompt;
        private Map<String, Toolkit> toolkits;
        private List<ToolDefinition> toolDefs;
        private ScratchpadToolkit localScratchpad;
        private ScratchpadToolkit sharedScratchpad;
        private List<KnowledgeEntry> knowledgeIndex;
        private Map<String, KnowledgeEntry> recalledKnowledge;

        Builder() {}

        Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        Builder toolkits(Map<String, Toolkit> toolkits) { this.toolkits = toolkits; return this; }
        Builder toolDefs(List<ToolDefinition> toolDefs) { this.toolDefs = toolDefs; return this; }
        Builder localScratchpad(ScratchpadToolkit localScratchpad) { this.localScratchpad = localScratchpad; return this; }
        Builder sharedScratchpad(ScratchpadToolkit sharedScratchpad) { this.sharedScratchpad = sharedScratchpad; return this; }
        Builder knowledgeIndex(List<KnowledgeEntry> knowledgeIndex) { this.knowledgeIndex = knowledgeIndex; return this; }
        Builder recalledKnowledge(Map<String, KnowledgeEntry> recalledKnowledge) { this.recalledKnowledge = recalledKnowledge; return this; }

        SmacAgentContext build() {

            return new SmacAgentContext(systemPrompt, toolkits, toolDefs, localScratchpad, sharedScratchpad,
                    knowledgeIndex, recalledKnowledge);
        }
    }
}
