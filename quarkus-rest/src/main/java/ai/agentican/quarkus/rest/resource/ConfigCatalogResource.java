package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanValidator;
import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.AgenticanConfig;
import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.rest.dto.CatalogImportSummary;
import ai.agentican.quarkus.rest.dto.CatalogSnapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/agentican/config")
public class ConfigCatalogResource {

    private static final String APPLICATION_YAML = "application/yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Inject
    Agentican agentican;

    @Inject
    AgenticanConfig config;

    @Inject
    ObjectMapper jsonMapper;

    @Inject
    CatalogAuditLog audit;

    @GET
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public CatalogSnapshot exportJson() {

        return buildSnapshot();
    }

    @GET
    @Path("/export.yaml")
    @Produces(APPLICATION_YAML)
    public Response exportYaml() throws Exception {

        var body = YAML.writerWithDefaultPrettyPrinter().writeValueAsString(buildSnapshot());
        return Response.ok(body, APPLICATION_YAML)
                .header("Content-Disposition", "attachment; filename=\"catalog.yaml\"")
                .build();
    }

    @POST
    @Path("/import")
    @Consumes({MediaType.APPLICATION_JSON, APPLICATION_YAML, "text/yaml", "application/x-yaml", MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public CatalogImportSummary importCatalog(
            String body,
            @HeaderParam("Content-Type") String contentType,
            @QueryParam("dryRun") boolean dryRun) {

        if (body == null || body.isBlank())
            throw new BadRequestException("Request body is empty");

        CatalogSnapshot snapshot;
        try {
            var mapper = isYaml(contentType) ? YAML : jsonMapper;
            snapshot = mapper.readValue(body, CatalogSnapshot.class);
        }
        catch (Exception e) {
            throw new BadRequestException("Failed to parse " + (isYaml(contentType) ? "YAML" : "JSON")
                    + ": " + e.getMessage());
        }

        return applySnapshot(snapshot, dryRun);
    }

    private CatalogSnapshot buildSnapshot() {

        var agents = agentican.registry().agents().getAll().stream()
                .filter(a -> a.config().externalId() != null)
                .map(a -> new CatalogSnapshot.AgentExport(
                        a.config().externalId(), a.name(), a.role(), a.config().llm()))
                .toList();

        var skills = agentican.registry().skills().getAll().stream()
                .filter(s -> s.externalId() != null)
                .map(s -> new CatalogSnapshot.SkillExport(s.externalId(), s.name(), s.instructions()))
                .toList();

        var plans = agentican.registry().plans().getAll().stream()
                .filter(p -> p.externalId() != null)
                .toList();

        return new CatalogSnapshot(agents, skills, plans);
    }

    private CatalogImportSummary applySnapshot(CatalogSnapshot snapshot, boolean dryRun) {

        var errors = new ArrayList<String>();

        var propertyAgentIds = propertyExternalIds(config.agents().stream().map(a -> a.externalId()).toList());
        var propertySkillIds = propertyExternalIds(config.skills().stream().map(s -> s.externalId()).toList());

        var agentCounts = importAgents(snapshot, propertyAgentIds, errors, dryRun);
        var skillCounts = importSkills(snapshot, propertySkillIds, errors, dryRun);
        var planCounts  = importPlans(snapshot, errors, dryRun);

        return new CatalogImportSummary(dryRun, agentCounts, skillCounts, planCounts, errors);
    }

    private CatalogImportSummary.Counts importAgents(CatalogSnapshot snapshot, Set<String> propertyIds,
                                                      List<String> errors, boolean dryRun) {

        var created = 0;
        var updated = 0;
        var skipped = 0;

        if (snapshot.agents() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var a : snapshot.agents()) {

            if (a.externalId() == null || a.externalId().isBlank()) {
                errors.add("Agent '" + a.name() + "' has no externalId; skipping");
                skipped++;
                continue;
            }

            if (propertyIds.contains(a.externalId())) {
                skipped++;
                continue;
            }

            var existing = agentican.registry().agents().getByExternalId(a.externalId());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing.config()) : null;
                    var id = existing != null ? existing.id() : Ids.generate();
                    var cfg = new AgentConfig(id, a.name(), a.role(), a.llm(), a.externalId());
                    agentican.registry().agents().register(agentican.buildAgent(cfg));
                    audit.record(CatalogAuditLog.AGENT, a.externalId(), CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(cfg));
                }
                catch (Exception e) {
                    errors.add("Agent '" + a.externalId() + "': " + e.getMessage());
                    continue;
                }
            }

            if (willCreate) created++; else updated++;
        }

        return new CatalogImportSummary.Counts(created, updated, skipped);
    }

    private CatalogImportSummary.Counts importSkills(CatalogSnapshot snapshot, Set<String> propertyIds,
                                                      List<String> errors, boolean dryRun) {

        var created = 0;
        var updated = 0;
        var skipped = 0;

        if (snapshot.skills() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var s : snapshot.skills()) {

            if (s.externalId() == null || s.externalId().isBlank()) {
                errors.add("Skill '" + s.name() + "' has no externalId; skipping");
                skipped++;
                continue;
            }

            if (propertyIds.contains(s.externalId())) {
                skipped++;
                continue;
            }

            var existing = agentican.registry().skills().getByExternalId(s.externalId());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing) : null;
                    var id = existing != null ? existing.id() : Ids.generate();
                    var cfg = new SkillConfig(id, s.name(), s.instructions(), s.externalId());
                    agentican.registry().skills().register(cfg);
                    audit.record(CatalogAuditLog.SKILL, s.externalId(), CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(cfg));
                }
                catch (Exception e) {
                    errors.add("Skill '" + s.externalId() + "': " + e.getMessage());
                    continue;
                }
            }

            if (willCreate) created++; else updated++;
        }

        return new CatalogImportSummary.Counts(created, updated, skipped);
    }

    private CatalogImportSummary.Counts importPlans(CatalogSnapshot snapshot, List<String> errors, boolean dryRun) {

        var created = 0;
        var updated = 0;
        var skipped = 0;

        if (snapshot.plans() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var p : snapshot.plans()) {

            if (p.externalId() == null || p.externalId().isBlank()) {
                errors.add("Plan '" + p.name() + "' has no externalId; skipping");
                skipped++;
                continue;
            }

            var issues = PlanValidator.validate(p, agentican.registry().agents(), agentican.registry().skills());
            if (!issues.isEmpty()) {
                errors.add("Plan '" + p.externalId() + "' failed validation: " + String.join("; ", issues));
                skipped++;
                continue;
            }

            var existing = agentican.registry().plans().getByExternalId(p.externalId());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing) : null;
                    var id = (p.id() == null || p.id().isBlank())
                            ? (existing != null ? existing.id() : Ids.generate())
                            : p.id();
                    var aligned = new Plan(id, p.name(), p.description(), p.params(), p.steps(),
                            p.externalId(), p.outputStep());
                    agentican.registry().plans().register(aligned);
                    audit.record(CatalogAuditLog.PLAN, p.externalId(), CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(aligned));
                }
                catch (Exception e) {
                    errors.add("Plan '" + p.externalId() + "': " + e.getMessage());
                    continue;
                }
            }

            if (willCreate) created++; else updated++;
        }

        return new CatalogImportSummary.Counts(created, updated, skipped);
    }

    private static Set<String> propertyExternalIds(List<java.util.Optional<String>> optionals) {

        var out = new HashSet<String>();
        optionals.forEach(o -> o.ifPresent(out::add));
        return out;
    }

    private String toJson(Object value) {

        try {
            return jsonMapper.writeValueAsString(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static boolean isYaml(String contentType) {

        if (contentType == null) return false;
        var lower = contentType.toLowerCase();
        return lower.contains("yaml");
    }
}
