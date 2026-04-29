package ai.agentican.quarkus.rest.dto;

import java.util.List;

public record CatalogImportSummary(

        boolean dryRun,

        Counts agents,

        Counts skills,

        Counts plans,

        List<String> errors) {

    public record Counts(

            int created,
            int updated,
            int skipped) {}
}
