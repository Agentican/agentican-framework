package ai.agentican.framework.util;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.framework.orchestration.planning.ToolView;
import ai.agentican.framework.tools.ToolResult;
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
    private final Template refineAgentStepMessageTemplate;
    private final Template refineControlStepMessageTemplate;
    private final String refineAgentStepPrompt;
    private final String refineControlStepPrompt;
    private final String factExtractionPrompt;

    public Templates() {

        this.userMessageTemplate = loadClasspathTemplate("templates/agent-user-message.txt");
        this.systemPromptTemplate = loadClasspathTemplate("templates/agent-system-prompt.txt");
        this.plannerPromptTemplate = loadClasspathTemplate("templates/plan-initial-system-prompt.txt");
        this.refineAgentStepMessageTemplate = loadClasspathTemplate("templates/plan-refine-agent-user-message.txt");
        this.refineControlStepMessageTemplate = loadClasspathTemplate("templates/plan-refine-control-user-message.txt");
        this.refineAgentStepPrompt = loadClasspathResource("templates/plan-refine-agent-system-prompt.txt");
        this.refineControlStepPrompt = loadClasspathResource("templates/plan-refine-control-system-prompt.txt");
        this.factExtractionPrompt = loadClasspathResource("templates/knowledge-system-prompt.txt");
    }

    public String renderSystemPrompt(String agentName, String agentDescription) {

        return renderSystemPrompt(agentName, agentDescription, List.of());
    }

    public String renderSystemPrompt(String agentName, String agentDescription, List<SkillConfig> skills) {

        return systemPromptTemplate
                .data("agentName", agentName != null ? agentName : "")
                .data("agentDescription", agentDescription != null ? agentDescription : "")
                .data("skills", skills != null ? skills : List.of())
                .render();
    }

    public String renderPlannerPrompt(Collection<Agent> agents, List<String> toolkits) {

        return plannerPromptTemplate
                .data("agents", agents != null ? agents : List.of())
                .data("toolkits", toolkits != null ? toolkits : List.of())
                .render();
    }

    public String renderUserMessage(String task, int iteration,
                                    List<ScratchpadEntry> scratchpadEntries, List<ToolResult> toolResults) {

        return renderUserMessage(task, iteration, scratchpadEntries, toolResults, List.of(), List.of());
    }

    public String renderUserMessage(String task, int iteration,
                                    List<ScratchpadEntry> scratchpadEntries, List<ToolResult> toolResults,
                                    List<KnowledgeEntry> knowledgeIndex, List<KnowledgeEntry> recalledKnowledge) {

        return userMessageTemplate
                .data("task", task)
                .data("iteration", iteration)
                .data("scratchpadEntries", scratchpadEntries != null ? scratchpadEntries : List.of())
                .data("toolResults", toolResults != null ? toolResults : List.of())
                .data("knowledgeIndex", knowledgeIndex != null ? knowledgeIndex : List.of())
                .data("recalledKnowledge", recalledKnowledge != null ? recalledKnowledge : List.of())
                .render();
    }

    public String refineAgentStepPrompt() {

        return refineAgentStepPrompt;
    }

    public String refineControlStepPrompt() {

        return refineControlStepPrompt;
    }

    public String factExtractionPrompt() {

        return factExtractionPrompt;
    }

    public String renderRefineAgentStepMessage(PlanStepAgent step, String agentRole, List<ToolView> tools) {

        return refineAgentStepMessageTemplate
                .data("step", step)
                .data("agentRole", agentRole != null ? agentRole : "")
                .data("tools", tools != null ? tools : List.of())
                .render();
    }

    public String renderRefineControlStepMessage(PlanStepLoop loop, PlanStepAgent producer,
                                                 List<ToolView> tools, Collection<Agent> agents) {

        // Extract body stepConfigs that are agent stepConfigs for the template
        var bodySteps = loop.body().stream()
                .filter(s -> s instanceof PlanStepAgent)
                .map(s -> (PlanStepAgent) s)
                .toList();

        return refineControlStepMessageTemplate
                .data("loop", loop)
                .data("producer", producer)
                .data("bodySteps", bodySteps)
                .data("tools", tools != null ? tools : List.of())
                .data("agents", agents != null ? agents : List.of())
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
