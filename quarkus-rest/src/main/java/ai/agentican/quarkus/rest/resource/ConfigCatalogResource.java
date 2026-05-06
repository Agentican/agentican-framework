package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowDefinitionValidator;
import ai.agentican.framework.util.Ids;
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
import java.util.List;

@Path("/agentican/config")
public class ConfigCatalogResource {

    private static final String APPLICATION_YAML = "application/yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Inject
    Agentican agentican;

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

        var agents = agentican.registry().agents().list().stream()
                .map(a -> new CatalogSnapshot.AgentExport(
                        a.id(), a.name(), a.role(), a.config().llm()))
                .toList();

        var skills = agentican.registry().skills().list().stream()
                .map(s -> new CatalogSnapshot.SkillExport(s.id(), s.name(), s.instructions()))
                .toList();

        var plans = agentican.registry().workflows().list().stream().toList();

        return new CatalogSnapshot(agents, skills, plans);
    }

    private CatalogImportSummary applySnapshot(CatalogSnapshot snapshot, boolean dryRun) {

        var errors = new ArrayList<String>();

        var agentCounts = importAgents(snapshot, errors, dryRun);
        var skillCounts = importSkills(snapshot, errors, dryRun);
        var planCounts  = importPlans(snapshot, errors, dryRun);

        return new CatalogImportSummary(dryRun, agentCounts, skillCounts, planCounts, errors);
    }

    private CatalogImportSummary.Counts importAgents(CatalogSnapshot snapshot,
                                                      List<String> errors, boolean dryRun) {

        var created = 0;
        var updated = 0;
        var skipped = 0;

        if (snapshot.agents() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var a : snapshot.agents()) {

            if (a.name() == null || a.name().isBlank()) {
                errors.add("Agent has no name; skipping");
                skipped++;
                continue;
            }

            var existing = agentican.registry().agents().byName(a.name());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing.config()) : null;
                    var id = existing != null ? existing.id() : Ids.generate();
                    var cfg = new AgentConfig(id, a.name(), a.role(), a.llm(), null, null, null);
                    agentican.registry().agents().register(cfg);
                    audit.record(CatalogAuditLog.AGENT, id, CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(cfg));
                }
                catch (Exception e) {
                    errors.add("Agent '" + a.name() + "': " + e.getMessage());
                    continue;
                }
            }

            if (willCreate) created++; else updated++;
        }

        return new CatalogImportSummary.Counts(created, updated, skipped);
    }

    private CatalogImportSummary.Counts importSkills(CatalogSnapshot snapshot,
                                                      List<String> errors, boolean dryRun) {

        var created = 0;
        var updated = 0;
        var skipped = 0;

        if (snapshot.skills() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var s : snapshot.skills()) {

            if (s.name() == null || s.name().isBlank()) {
                errors.add("Skill has no name; skipping");
                skipped++;
                continue;
            }

            var existing = agentican.registry().skills().byName(s.name());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing) : null;
                    var id = existing != null ? existing.id() : Ids.generate();
                    var cfg = new SkillConfig(id, s.name(), s.instructions());
                    agentican.registry().skills().register(cfg);
                    audit.record(CatalogAuditLog.SKILL, id, CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(cfg));
                }
                catch (Exception e) {
                    errors.add("Skill '" + s.name() + "': " + e.getMessage());
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

        if (snapshot.workflows() == null) return new CatalogImportSummary.Counts(0, 0, 0);

        for (var p : snapshot.workflows()) {

            if (p.name() == null || p.name().isBlank()) {
                errors.add("WorkflowDefinition has no name; skipping");
                skipped++;
                continue;
            }

            var issues = WorkflowDefinitionValidator.validate(p, agentican.registry().agents(), agentican.registry().skills());
            if (!issues.isEmpty()) {
                errors.add("WorkflowDefinition '" + p.name() + "' failed validation: " + String.join("; ", issues));
                skipped++;
                continue;
            }

            var existing = agentican.registry().workflows().byName(p.name());
            var willCreate = (existing == null);

            if (!dryRun) {
                try {
                    var beforeJson = existing != null ? toJson(existing) : null;
                    var id = (p.id() == null || p.id().isBlank())
                            ? (existing != null ? existing.id() : Ids.generate())
                            : p.id();
                    var aligned = new WorkflowDefinition(id, p.name(), p.description(), p.params(), p.steps(), p.outputStep());
                    agentican.registry().workflows().register(aligned);
                    audit.record(CatalogAuditLog.PLAN, id, CatalogAuditLog.IMPORTED,
                            null, beforeJson, toJson(aligned));
                }
                catch (Exception e) {
                    errors.add("WorkflowDefinition '" + p.name() + "': " + e.getMessage());
                    continue;
                }
            }

            if (willCreate) created++; else updated++;
        }

        return new CatalogImportSummary.Counts(created, updated, skipped);
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
