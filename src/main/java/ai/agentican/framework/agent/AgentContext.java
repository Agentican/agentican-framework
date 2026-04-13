package ai.agentican.framework.agent;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.tools.scratchpad.ScratchpadToolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AgentContext(
        String systemPrompt,
        Map<String, Toolkit> toolkits,
        List<ToolDefinition> toolDefs,
        ScratchpadToolkit scratchpad,
        List<KnowledgeEntry> knowledgeIndex,
        Map<String, KnowledgeEntry> recalledKnowledge) {

    AgentContext(String systemPrompt, Map<String, Toolkit> toolkits, List<ToolDefinition> toolDefs,
                 ScratchpadToolkit scratchpad, List<KnowledgeEntry> knowledgeIndex) {

        this(systemPrompt, toolkits, toolDefs, scratchpad, knowledgeIndex, new LinkedHashMap<>());
    }
}
