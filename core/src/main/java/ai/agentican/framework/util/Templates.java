package ai.agentican.framework.util;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentToolUse;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.planning.ToolView;
import ai.agentican.framework.tools.scratchpad.ScratchpadEntry;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class Templates {

    private final Template userMessageTemplate;
    private final Template systemPromptTemplate;
    private final Template plannerPromptTemplate;
    private final Template refinePlanMessageTemplate;
    private final Template factExtractionPromptTemplate;
    private final String refinePlanPrompt;

    public Templates() {

        this.userMessageTemplate = loadClasspathTemplate("templates/agent-user-message.txt");
        this.systemPromptTemplate = loadClasspathTemplate("templates/agent-system-prompt.txt");
        this.plannerPromptTemplate = loadClasspathTemplate("templates/plan-initial-system-prompt.txt");
        this.refinePlanMessageTemplate = loadClasspathTemplate("templates/plan-refine-user-message.txt");
        this.factExtractionPromptTemplate = loadClasspathTemplate("templates/knowledge-system-prompt.txt");
        this.refinePlanPrompt = loadClasspathResource("templates/plan-refine-system-prompt.txt");
    }

    public String renderSystemPrompt(String agentName, String agentDescription) {

        return renderSystemPrompt(agentName, agentDescription, List.of(), false);
    }

    public String renderSystemPrompt(String agentName, String agentDescription, List<SkillConfig> skills) {

        return renderSystemPrompt(agentName, agentDescription, skills, false);
    }

    public String renderSystemPrompt(String agentName, String agentDescription, List<SkillConfig> skills,
                                     boolean structuredOutput) {

        return systemPromptTemplate
                .data("agentName", agentName != null ? agentName : "")
                .data("agentDescription", agentDescription != null ? agentDescription : "")
                .data("skills", skills != null ? skills : List.of())
                .data("structuredOutput", structuredOutput)
                .render();
    }

    public String renderPlannerPrompt(Collection<Agent> agents, Collection<SkillConfig> skills,
                                      List<String> tools, Collection<Plan> existingPlans) {

        return plannerPromptTemplate
                .data("agents", agents != null ? agents : List.of())
                .data("skills", skills != null ? skills : List.of())
                .data("tools", tools != null ? tools : List.of())
                .data("existingPlans", existingPlans != null ? existingPlans : List.of())
                .render();
    }

    public String renderTaskBlock(String task) {

        return "<task>\n  " + (task != null ? task : "") + "\n</task>\n";
    }

    public String renderUserMessage(int iteration,
                                    List<ScratchpadEntry> localScratchpadEntries,
                                    List<ScratchpadEntry> sharedScratchpadEntries,
                                    List<AgentToolUse> progress,
                                    List<KnowledgeEntry> knowledgeIndex,
                                    List<KnowledgeEntry> recalledKnowledge) {

        return userMessageTemplate
                .data("iteration", iteration)
                .data("localScratchpadEntries", localScratchpadEntries != null ? localScratchpadEntries : List.of())
                .data("sharedScratchpadEntries", sharedScratchpadEntries != null ? sharedScratchpadEntries : List.of())
                .data("progress", progress != null ? progress : List.of())
                .data("knowledgeIndex", knowledgeIndex != null ? knowledgeIndex : List.of())
                .data("recalledKnowledge", recalledKnowledge != null ? recalledKnowledge : List.of())
                .render();
    }

    public String refinePlanPrompt() {

        return refinePlanPrompt;
    }

    public String renderFactExtractionPrompt(List<KnowledgeEntry> existingEntries) {

        return factExtractionPromptTemplate
                .data("existingEntries", existingEntries != null ? existingEntries : List.of())
                .render();
    }

    public String renderRefinePlanMessage(String planJson,
                                          Collection<Agent> agents,
                                          Collection<SkillConfig> skills,
                                          List<ToolView> tools) {

        return refinePlanMessageTemplate
                .data("planJson", planJson != null ? planJson : "{}")
                .data("agents", agents != null ? agents : List.of())
                .data("skills", skills != null ? skills : List.of())
                .data("tools", tools != null ? tools : List.of())
                .render();
    }

    private static String loadClasspathResource(String resourcePath) {

        try (var is = Templates.class.getClassLoader().getResourceAsStream(resourcePath)) {

            if (is == null)
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {

            throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
        }
    }

    private static Template loadClasspathTemplate(String resourcePath) {

        try (var is = Templates.class.getClassLoader().getResourceAsStream(resourcePath)) {

            if (is == null)
                throw new IllegalStateException("Default template not found on classpath: " + resourcePath);

            var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            return Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver()).build().parse(content);
        }
        catch (IOException e) {

            throw new IllegalStateException("Failed to load default template", e);
        }
    }

    private static Template loadFileTemplate(Path path) {

        try {

            var content = Files.readString(path);

            return Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver()).build().parse(content);
        }
        catch (IOException e) {

            throw new IllegalStateException("Failed to load template from " + path, e);
        }
    }
}
