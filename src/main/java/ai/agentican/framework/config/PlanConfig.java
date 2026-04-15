package ai.agentican.framework.config;

import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.orchestration.model.PlanParam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanConfig(
        String name,
        String description,
        List<PlanParamConfig> paramConfigs,
        List<PlanStepConfig> stepConfigs,
        String externalId) {

    public PlanConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Task name is required");

        if (stepConfigs == null)
            stepConfigs = List.of();

        if (paramConfigs == null)
            paramConfigs = List.of();

        if (externalId != null && externalId.isBlank())
            externalId = null;
    }

    public PlanConfig(String name, String description, List<PlanParamConfig> paramConfigs,
                      List<PlanStepConfig> stepConfigs) {

        this(name, description, paramConfigs, stepConfigs, null);
    }

    public Plan toPlan() {

        var params = paramConfigs.stream().map(tpc ->
                        PlanParam.of(tpc.name(), tpc.description(), tpc.defaultValue(), tpc.required()))
                .toList();

        var steps = stepConfigs.stream().map(PlanStepConfig::toPlanStep).toList();

        return Plan.withExternalId(externalId, name, description, params, steps);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanParamConfig(
            String name,
            String description,
            String defaultValue,
            boolean required) {

        public PlanParamConfig {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Parameter name is required");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanStepConfig(
            String name,
            String type,
            String agent,
            String instructions,
            List<String> dependencies,
            boolean hitl,
            List<String> skills,
            List<String> tools,
            String over,
            String from,
            List<BranchPathConfig> pathConfigs,
            String defaultPath,
            List<PlanStepConfig> stepConfigs) {

        public PlanStepConfig {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Step name is required");

            if (type == null)
                type = "agent";

            if (dependencies == null)
                dependencies = List.of();

            if (skills == null)
                skills = List.of();

            if (tools == null)
                tools = List.of();
        }

        public PlanStep toPlanStep() {

            return switch (type) {

                case "loop" -> {

                    List<PlanStep> steps;

                    if (stepConfigs != null && !stepConfigs.isEmpty()) {

                        steps = stepConfigs.stream().map(PlanStepConfig::toPlanStep).toList();
                    }
                    else {

                        var stepName = name + "-body";

                        var step = PlanStepAgent.of(stepName, agent, instructions, List.of(), false, skills, tools);

                        steps = List.of(step);
                    }

                    yield new PlanStepLoop(name, over, steps, dependencies, hitl);
                }

                case "branch" -> {

                    var paths = this.pathConfigs.stream()
                            .map(bpc -> PlanStepBranch.Path.of(bpc.pathName(), bpc.toPlanStep()))
                            .toList();

                    yield PlanStepBranch.of(name, from, paths, defaultPath, dependencies, hitl);
                }

                default -> PlanStepAgent.of(name, agent, instructions, dependencies, hitl, skills, tools);
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchPathConfig(
            String pathName,
            String agent,
            String instructions,
            List<String> tools,
            List<PlanStepConfig> stepConfigs) {

        public BranchPathConfig {

            if (pathName == null || pathName.isBlank())
                throw new IllegalArgumentException("Path name is required");

            if (tools == null)
                tools = List.of();
        }

        List<PlanStep> toPlanStep() {

            if (stepConfigs != null && !stepConfigs.isEmpty())
                return stepConfigs.stream().map(PlanStepConfig::toPlanStep).toList();

            var stepName = pathName + "-body";

            var agentStep =
                    PlanStepAgent.of(stepName, agent, instructions, List.of(), false, List.of(), tools);

            return List.of(agentStep);
        }
    }
}
