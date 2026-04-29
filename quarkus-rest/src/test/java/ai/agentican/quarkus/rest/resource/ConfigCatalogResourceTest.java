package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ConfigCatalogResourceTest {

    @BeforeAll
    static void allowYamlBodyEncoding() {

        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    @Test
    void exportJsonReturnsCurrentCatalog() {

        given().when().get("/agentican/config/export")
                .then()
                .statusCode(200)
                .body("agents", notNullValue())
                .body("skills", notNullValue())
                .body("plans", notNullValue());
    }

    @Test
    void exportYamlReturnsYamlAttachment() {

        given().when().get("/agentican/config/export.yaml")
                .then()
                .statusCode(200)
                .contentType(anyOf(containsString("yaml"), containsString("YAML")))
                .header("Content-Disposition", containsString("catalog.yaml"))
                .body(containsString("agents"))
                .body(containsString("skills"))
                .body(containsString("plans"));
    }

    @Test
    void dryRunImportReportsCountsWithoutMutating() {

        var externalId = "dry-imp-" + System.nanoTime();

        var body = """
                {
                  "agents": [
                    {"externalId": "%s", "name": "dry-analyst", "role": "Reviews", "llm": null}
                  ],
                  "skills": [],
                  "plans": []
                }
                """.formatted(externalId);

        given().contentType("application/json").body(body).queryParam("dryRun", true)
                .when().post("/agentican/config/import")
                .then()
                .statusCode(200)
                .body("dryRun", is(true))
                .body("agents.created", is(1))
                .body("errors", hasSize(0));

        given().when().get("/agentican/config/export")
                .then()
                .statusCode(200)
                .body("agents.externalId", org.hamcrest.Matchers.not(hasItem(externalId)));
    }

    @Test
    void importRoundTripCreatesThenUpdates() {

        var externalId = "imp-" + System.nanoTime();

        var createBody = """
                {
                  "agents": [
                    {"externalId": "%s", "name": "imp-analyst", "role": "Initial role", "llm": null}
                  ],
                  "skills": [],
                  "plans": []
                }
                """.formatted(externalId);

        try {

            given().contentType("application/json").body(createBody)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("dryRun", is(false))
                    .body("agents.created", is(1))
                    .body("agents.updated", is(0));

            var updateBody = """
                    {
                      "agents": [
                        {"externalId": "%s", "name": "imp-analyst", "role": "Updated role", "llm": null}
                      ],
                      "skills": [],
                      "plans": []
                    }
                    """.formatted(externalId);

            given().contentType("application/json").body(updateBody)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("agents.created", is(0))
                    .body("agents.updated", is(1));

            given().when().get("/agentican/agents/" + externalId)
                    .then()
                    .statusCode(200)
                    .body("role", equalTo("Updated role"));
        }
        finally {

            given().when().delete("/agentican/agents/" + externalId).then()
                    .statusCode(anyOf(is(204), is(404)));
        }
    }

    @Test
    void importSkipsPropertyDeclaredEntries() {

        var body = """
                {
                  "agents": [
                    {"externalId": "researcher", "name": "researcher", "role": "overridden by import", "llm": null}
                  ],
                  "skills": [],
                  "plans": []
                }
                """;

        given().contentType("application/json").body(body)
                .when().post("/agentican/config/import")
                .then()
                .statusCode(200)
                .body("agents.skipped", greaterThanOrEqualTo(1))
                .body("agents.created", is(0))
                .body("agents.updated", is(0));

        given().when().get("/agentican/agents/researcher")
                .then()
                .statusCode(200)
                .body("role", equalTo("Expert at finding information"));
    }

    @Test
    void importInvalidPlanRecordsError() {

        var externalId = "bad-imp-plan-" + System.nanoTime();

        var body = """
                {
                  "agents": [],
                  "skills": [],
                  "plans": [
                    {
                      "name": "imp-bad",
                      "externalId": "%s",
                      "outputStep": "ghost",
                      "params": [],
                      "steps": [
                        {
                          "type": "agent",
                          "name": "ghost",
                          "agentId": "nonexistent-agent",
                          "instructions": "work",
                          "dependencies": [],
                          "hitl": false,
                          "skills": [],
                          "tools": []
                        }
                      ]
                    }
                  ]
                }
                """.formatted(externalId);

        given().contentType("application/json").body(body)
                .when().post("/agentican/config/import")
                .then()
                .statusCode(200)
                .body("plans.skipped", is(1))
                .body("plans.created", is(0))
                .body("errors", hasSize(1));
    }

    @Test
    void importYamlBodyParses() {

        var externalId = "yaml-imp-" + System.nanoTime();

        var body = """
                agents:
                  - externalId: %s
                    name: yaml-analyst
                    role: Came in via YAML
                    llm: null
                skills: []
                plans: []
                """.formatted(externalId);

        try {

            given().contentType("application/yaml").body(body)
                    .when().post("/agentican/config/import")
                    .then()
                    .statusCode(200)
                    .body("agents.created", is(1))
                    .body("errors", hasSize(0));
        }
        finally {

            given().when().delete("/agentican/agents/" + externalId).then()
                    .statusCode(anyOf(is(204), is(404)));
        }
    }

    @Test
    void importEmptyBodyReturns400() {

        given().contentType("application/json").body("")
                .when().post("/agentican/config/import")
                .then()
                .statusCode(400);
    }
}
